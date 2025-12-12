output "budget_topic_name" {
  description = "Name of the Pub/Sub topic for budget alerts"
  value       = google_pubsub_topic.budget_alerts.name
}

output "budget_topic_id" {
  description = "Full resource ID of the Pub/Sub topic"
  value       = google_pubsub_topic.budget_alerts.id
}

output "function_name" {
  description = "Name of the deployed Cloud Function"
  value       = google_cloudfunctions_function.budget_enforcer.name
}

output "function_url" {
  description = "URL of the deployed Cloud Function"
  value       = google_cloudfunctions_function.budget_enforcer.https_trigger_url
}

output "function_service_account" {
  description = "Email of the function's service account"
  value       = google_service_account.budget_enforcer.email
}

output "budget_id" {
  description = "ID of the created budget (if created)"
  value       = var.create_budget ? google_billing_budget.monthly_budget[0].name : "Not created - set create_budget=true and provide billing_account_id"
}

output "setup_instructions" {
  description = "Instructions for completing the setup"
  value       = <<-EOT
    Budget enforcement infrastructure deployed successfully!
    
    Next steps:
    
    1. Create the billing budget manually (requires billing account permissions):
       
       gcloud billing budgets create \
         --billing-account=${var.billing_account_id != "" ? var.billing_account_id : "YOUR_BILLING_ACCOUNT_ID"} \
         --display-name="${var.budget_display_name}" \
         --budget-amount=${var.budget_amount} \
         --threshold-rule=percent=50 \
         --threshold-rule=percent=80 \
         --threshold-rule=percent=100 \
         --notifications-rule-pubsub-topic=${google_pubsub_topic.budget_alerts.id}
    
    2. Test the function:
       
       gcloud pubsub topics publish ${google_pubsub_topic.budget_alerts.name} \
         --message='{"budgetDisplayName":"Test Budget","alertThresholdExceeded":1.0,"costAmount":100,"budgetAmount":100}'
    
    3. Check function logs:
       
       gcloud functions logs read ${google_cloudfunctions_function.budget_enforcer.name} --region=${var.region} --limit=50
    
    4. View budget status:
       
       gcloud billing budgets list --billing-account=${var.billing_account_id != "" ? var.billing_account_id : "YOUR_BILLING_ACCOUNT_ID"}
    
    For more information, see docs/gcp-budget-enforcement.md
  EOT
}
