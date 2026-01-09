import json
import sys

def get_string_value(field):
    if 'stringValue' in field:
        return field['stringValue']
    return None

def get_map_value(field):
    if 'mapValue' in field and 'fields' in field['mapValue']:
        return field['mapValue']['fields']
    return {}

try:
    with open('receipts_dump_new.json', 'r') as f:
        data = json.load(f)
except json.JSONDecodeError:
    print("Error: Invalid JSON in receipts_dump.json")
    sys.exit(1)

if 'documents' not in data:
    print("No documents found.")
    sys.exit(0)

errors_found = []

for doc in data['documents']:
    fields = doc.get('fields', {})
    
    # Get object name/path for identification
    object_name = get_string_value(fields.get('objectName', {}))
    if not object_name:
         object_path = get_string_value(fields.get('objectPath', {}))
         if object_path:
             object_name = object_path.split('/')[-1]
         else:
             object_name = doc['name'].split('/')[-1]

    # Check for top-level error
    top_level_error = get_string_value(fields.get('error', {}))
    
    # Check for data.errors
    data_field = get_map_value(fields.get('data', {}))
    inner_errors = []
    
    if 'errors' in data_field:
        errors_list = data_field['errors'].get('arrayValue', {}).get('values', [])
        for error_item in errors_list:
            error_map = get_map_value(error_item)
            msg = get_string_value(error_map.get('message', {}))
            line_content = get_string_value(error_map.get('content', {}))
            if msg:
                if line_content:
                    inner_errors.append(f"{msg} (Line: '{line_content}')")
                else:
                    inner_errors.append(msg)

    if top_level_error:
        errors_found.append(f"File: {object_name}\nError: {top_level_error}")
    
    if inner_errors:
        errors_found.append(f"File: {object_name}\nParsing Errors:\n" + "\n".join([f"- {e}" for e in inner_errors]))

if errors_found:
    print("\n\n".join(errors_found))
else:
    print("No parsing errors found.")
