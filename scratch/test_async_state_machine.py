import requests
import json
import time

BASE_URL = "http://localhost:9091/api"

def run_tests():
    print("=== STARTING SPRINT 8 STATEFUL ENGINE & ASYNC RESUME TESTS ===")

    # 1. Create a Bucket registry item
    bucket_id = "approval_queue"
    bucket_payload = {
        "bucketId": bucket_id,
        "name": "Manual Approval Queue",
        "description": "Bucket queue for manual approvals",
        "category": "Approvals",
        "priority": "HIGH",
        "slaHours": 24,
        "active": True
    }
    print(f"\n1. Creating bucket '{bucket_id}'...")
    res = requests.post(f"{BASE_URL}/buckets", json=bucket_payload)
    if res.status_code == 201:
        print("Bucket created successfully.")
    else:
        # It might already exist from database seeding, try to verify
        print(f"Bucket creation returned {res.status_code}. Checking if it exists...")
        check_res = requests.get(f"{BASE_URL}/buckets/key/{bucket_id}")
        assert check_res.status_code == 200, f"Bucket does not exist: {check_res.text}"
        print("Bucket exists in registry.")

    # 2. Create Workflow Definition
    ts = int(time.time())
    wf_key = f"verify_stateful_{ts}"
    wf_payload = {
        "key": wf_key,
        "name": f"Stateful Engine Test {ts}",
        "description": "Integration test for Sprint 8 stateful loop with suspension & async resume"
    }
    
    print(f"\n2. Creating workflow definition '{wf_key}'...")
    res = requests.post(f"{BASE_URL}/workflows", json=wf_payload)
    assert res.status_code == 201, f"Failed to create workflow: {res.text}"
    wf_data = res.json()
    wf_id = wf_data["id"]
    print(f"Workflow created successfully with ID: {wf_id}")

    version_id = wf_data["versions"][0]["id"]
    print(f"Draft Version ID: {version_id}")

    # 3. Create the definition graph for the draft version
    # Graph: START -> BUCKET (approval_queue) -> RULE (check status == RESOLVED) -> END
    graph = {
        "nodes": [
            {
                "id": "start-1",
                "type": "START",
                "label": "Start",
                "position": {"x": 250, "y": 50},
                "data": {}
            },
            {
                "id": "bucket-1",
                "type": "BUCKET",
                "label": "Approval Bucket Task",
                "position": {"x": 250, "y": 180},
                "data": {
                    "bucketId": "approval_queue"
                }
            },
            {
                "id": "rule-1",
                "type": "RULE",
                "label": "Check Resolution Success",
                "position": {"x": 250, "y": 300},
                "data": {
                    "expression": "context['approval_queue_status'] == 'RESOLVED'"
                }
            },
            {
                "id": "end-1",
                "type": "END",
                "label": "End",
                "position": {"x": 250, "y": 420},
                "data": {}
            }
        ],
        "edges": [
            {
                "id": "e-1",
                "source": "start-1",
                "target": "bucket-1"
            },
            {
                "id": "e-2",
                "source": "bucket-1",
                "target": "rule-1"
            },
            {
                "id": "e-3",
                "source": "rule-1",
                "target": "end-1",
                "data": {
                    "condition": "true"
                }
            }
        ],
        "metadata": {}
    }

    print("\n3. Updating draft version graph...")
    res = requests.put(f"{BASE_URL}/workflows/versions/{version_id}", json=graph)
    assert res.status_code == 200, f"Failed to update draft graph: {res.text}"
    print("Draft graph updated successfully.")

    # 4. Transition status: DRAFT -> REVIEW -> APPROVED -> PUBLISHED
    transitions = ["REVIEW", "APPROVED", "PUBLISHED"]
    print("\n4. Transitioning version lifecycle status to PUBLISHED...")
    for status in transitions:
        res = requests.put(f"{BASE_URL}/workflows/versions/{version_id}/status", json={"status": status})
        assert res.status_code == 200, f"Failed transition to {status}: {res.text}"
        print(f"  Successfully transitioned to status: {status}")

    # 5. Execute Workflow (Initial Run)
    # Expected behavior:
    # Traversal runs START -> BUCKET, suspends.
    # An active WorkflowInstance is created in status WAITING.
    # A BucketExecution task is created in status PENDING.
    exec_payload = {
        "contextId": f"test-stateful-ctx-{ts}",
        "context": {
            "initial_param": "some_value"
        }
    }

    print(f"\n5. Executing workflow '{wf_key}' (expecting suspend)...")
    res = requests.post(f"{BASE_URL}/execute/{wf_key}", json=exec_payload)
    assert res.status_code == 201, f"Execution request failed: {res.text}"
    exec_data = res.json()
    
    print(f"Execution status: {exec_data['status']}")
    print(f"Last logged node in trace: {exec_data['executionTrace'][-1]['label']} ({exec_data['executionTrace'][-1]['nodeType']})")
    assert exec_data['status'] == "WAITING", f"Expected execution status to be WAITING, got: {exec_data['status']}"
    
    instance_id = exec_data.get("instanceId")
    assert instance_id is not None, "Execution did not return a stateful instanceId!"
    print(f"Created WorkflowInstance ID: {instance_id}")

    # 6. Fetch WorkflowInstance details
    print(f"\n6. Fetching WorkflowInstance '{instance_id}'...")
    res = requests.get(f"{BASE_URL}/instances/{instance_id}")
    assert res.status_code == 200, f"Failed to fetch instance: {res.text}"
    instance_data = res.json()
    print(f"Instance status: {instance_data['status']}")
    print(f"Instance current node: {instance_data['currentNodeLabel']} ({instance_data['currentNodeId']})")
    assert instance_data['status'] == "WAITING", f"Expected instance status to be WAITING, got {instance_data['status']}"
    assert instance_data['currentNodeId'] == "bucket-1", f"Expected current position to be 'bucket-1', got {instance_data['currentNodeId']}"

    # 7. Locate the PENDING BucketExecution task
    print(f"\n7. Fetching pending bucket executions for bucket '{bucket_id}'...")
    res = requests.get(f"{BASE_URL}/bucket-executions?status=PENDING")
    assert res.status_code == 200, f"Failed to fetch bucket tasks: {res.text}"
    tasks = res.json()
    
    # Filter for our instance
    our_task = None
    for t in tasks:
        if t.get("instanceId") == instance_id and t.get("bucketId") == bucket_id:
            our_task = t
            break
            
    assert our_task is not None, "Could not find PENDING bucket execution for our instance!"
    task_id = our_task["id"]
    print(f"Found pending task ID: {task_id}")

    # 8. Resolve the BucketExecution task
    # Expected behavior:
    # 1. BucketExecution transitions to RESOLVED.
    # 2. Local listener catches BucketCompletedEvent.
    # 3. Resumes instance starting from BUCKET node's outgoing edges.
    # 4. Context gets approval_queue_status = 'RESOLVED'.
    # 5. RULE node evaluates this condition to True.
    # 6. Traversal completes at END node.
    # 7. WorkflowInstance status transitions to COMPLETED.
    print(f"\n8. Resolving bucket task '{task_id}'...")
    resolve_payload = {
        "resolvedBy": "TestRunner",
        "notes": "Verified approval requirements"
    }
    res = requests.post(f"{BASE_URL}/bucket-executions/{task_id}/resolve", json=resolve_payload)
    assert res.status_code == 200, f"Failed to resolve task: {res.text}"
    resolved_task = res.json()
    print(f"Task status after resolution: {resolved_task['status']}")
    assert resolved_task['status'] == "RESOLVED", f"Expected task status to be RESOLVED, got {resolved_task['status']}"

    # Wait briefly for event-driven async execution to run in the background
    print("\n9. Waiting 2 seconds for event listener and resumption...")
    time.sleep(2)

    # 9. Verify WorkflowInstance is now COMPLETED
    print(f"Checking WorkflowInstance '{instance_id}' status...")
    res = requests.get(f"{BASE_URL}/instances/{instance_id}")
    assert res.status_code == 200, f"Failed to fetch instance: {res.text}"
    instance_data = res.json()
    print(f"Instance status: {instance_data['status']}")
    print(f"Instance context map: {json.dumps(instance_data['context'])}")
    
    assert instance_data['status'] == "COMPLETED", f"Expected instance status to be COMPLETED, got {instance_data['status']}"
    assert instance_data['context'].get("approval_queue_status") == "RESOLVED", "Expected approval_queue_status to be RESOLVED in context!"
    print("\n=== SPRINT 8 STATEFUL ENGINE & ASYNC RESUME TESTS PASSED SUCCESSFULLY! ===")

if __name__ == "__main__":
    run_tests()
