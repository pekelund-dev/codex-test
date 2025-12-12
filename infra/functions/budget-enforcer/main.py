"""
Budget Enforcer Cloud Function

This function is triggered by Pub/Sub budget alerts and automatically stops
Cloud Run services when the budget threshold reaches 100%.

Environment Variables:
    PROJECT_ID: GCP project ID
    REGION: GCP region where Cloud Run services are deployed

Pub/Sub Message Format:
    The function expects budget alert messages in the format:
    {
        "budgetDisplayName": "Budget Name",
        "alertThresholdExceeded": 1.0,  # 1.0 = 100%
        "costAmount": 100.0,
        "budgetAmount": 100.0
    }
"""

import base64
import json
import logging
import os
from typing import Dict, List, Any

from google.cloud import run_v2
from google.api_core import exceptions

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration
PROJECT_ID = os.environ.get('PROJECT_ID')
REGION = os.environ.get('REGION', 'us-east1')

# Cloud Run services to stop when budget is exceeded
# Customize this list based on your services
SERVICES_TO_STOP = [
    'pklnd-web',
    'pklnd-receipts',
]

# Budget threshold at which to stop services (1.0 = 100%)
STOP_THRESHOLD = 1.0


def stop_services(event: Dict[str, Any], context: Any) -> None:
    """
    Cloud Function entry point triggered by Pub/Sub budget alerts.
    
    Args:
        event: Pub/Sub event containing budget alert data
        context: Event context (unused)
    """
    try:
        # Decode Pub/Sub message
        pubsub_message = base64.b64decode(event['data']).decode('utf-8')
        budget_alert = json.loads(pubsub_message)
        
        logger.info(f"Received budget alert: {budget_alert}")
        
        # Extract alert details
        budget_name = budget_alert.get('budgetDisplayName', 'Unknown')
        threshold_exceeded = budget_alert.get('alertThresholdExceeded', 0)
        cost_amount = budget_alert.get('costAmount', 0)
        budget_amount = budget_alert.get('budgetAmount', 0)
        
        logger.info(
            f"Budget: {budget_name}, "
            f"Threshold: {threshold_exceeded:.0%}, "
            f"Cost: ${cost_amount:.2f}, "
            f"Budget: ${budget_amount:.2f}"
        )
        
        # Check if threshold meets or exceeds the stop threshold
        if threshold_exceeded >= STOP_THRESHOLD:
            logger.warning(
                f"Budget threshold {threshold_exceeded:.0%} reached! "
                f"Stopping Cloud Run services..."
            )
            
            stopped_services = stop_cloud_run_services()
            
            if stopped_services:
                logger.info(
                    f"Successfully stopped {len(stopped_services)} service(s): "
                    f"{', '.join(stopped_services)}"
                )
            else:
                logger.warning("No services were stopped")
        else:
            logger.info(
                f"Budget threshold {threshold_exceeded:.0%} below stop threshold "
                f"{STOP_THRESHOLD:.0%}. No action taken."
            )
            
    except json.JSONDecodeError as e:
        logger.error(f"Failed to parse budget alert message: {e}")
        logger.error(f"Raw message: {event.get('data', 'No data')}")
    except KeyError as e:
        logger.error(f"Missing required field in budget alert: {e}")
    except Exception as e:
        logger.error(f"Unexpected error processing budget alert: {e}", exc_info=True)


def stop_cloud_run_services() -> List[str]:
    """
    Stop all configured Cloud Run services by setting max instances to 0.
    
    Returns:
        List of service names that were successfully stopped
    """
    if not PROJECT_ID:
        logger.error("PROJECT_ID environment variable not set")
        return []
    
    stopped_services = []
    client = run_v2.ServicesClient()
    
    for service_name in SERVICES_TO_STOP:
        try:
            # Construct the full service name
            parent = f"projects/{PROJECT_ID}/locations/{REGION}"
            full_service_name = f"{parent}/services/{service_name}"
            
            logger.info(f"Attempting to stop service: {service_name}")
            
            # Get the current service configuration
            try:
                service = client.get_service(name=full_service_name)
            except exceptions.NotFound:
                logger.warning(f"Service not found: {service_name}")
                continue
            
            # Update service to stop it (set max instances to 0)
            # This prevents new instances from starting
            service.template.scaling.max_instance_count = 0
            
            # Apply the update
            operation = client.update_service(service=service)
            
            # Wait for the operation to complete
            logger.info(f"Waiting for service {service_name} to stop...")
            result = operation.result(timeout=300)  # 5 minute timeout
            
            logger.info(f"Successfully stopped service: {service_name}")
            stopped_services.append(service_name)
            
        except exceptions.PermissionDenied as e:
            logger.error(
                f"Permission denied stopping service {service_name}. "
                f"Ensure the function's service account has roles/run.admin permission. "
                f"Error: {e}"
            )
        except exceptions.NotFound as e:
            logger.warning(f"Service {service_name} not found: {e}")
        except Exception as e:
            logger.error(
                f"Failed to stop service {service_name}: {e}",
                exc_info=True
            )
    
    return stopped_services


def list_all_services() -> List[str]:
    """
    List all Cloud Run services in the project (for debugging).
    
    Returns:
        List of service names
    """
    if not PROJECT_ID:
        logger.error("PROJECT_ID environment variable not set")
        return []
    
    try:
        client = run_v2.ServicesClient()
        parent = f"projects/{PROJECT_ID}/locations/{REGION}"
        
        services = []
        for service in client.list_services(parent=parent):
            service_name = service.name.split('/')[-1]
            services.append(service_name)
            logger.info(f"Found service: {service_name}")
        
        return services
        
    except Exception as e:
        logger.error(f"Failed to list services: {e}", exc_info=True)
        return []
