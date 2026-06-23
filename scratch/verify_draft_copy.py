import urllib.request
import urllib.parse
import json

BASE_URL = "http://localhost:9091/api/workflows"

def post_json(url, data):
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req) as res:
        return json.loads(res.read().decode("utf-8"))

def put_json(url, data):
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="PUT"
    )
    with urllib.request.urlopen(req) as res:
        return json.loads(res.read().decode("utf-8"))

def get_json(url):
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req) as res:
        return json.loads(res.read().decode("utf-8"))

def main():
    print("1. Creating a new workflow definition...")
    wf_key = f"TEST_COPY_FLOW_{int(urllib.request.urlopen('http://localhost:9091/api/workflows').length or 99)}"
    wf_data = {
        "name": "Test Copy Structure",
        "key": wf_key,
        "description": "Validating new draft structure copy logic"
    }
    
    # Create workflow
    created_wf = post_json(BASE_URL, wf_data)
    wf_id = created_wf["id"]
    v1 = created_wf["versions"][0]
    v1_id = v1["id"]
    print(f"Workflow created with ID {wf_id}, Version 1 ID {v1_id}")
    
    print("2. Designing custom nodes and edges for Version 1...")
    custom_graph = {
        "nodes": [
            {"id": "start-1", "type": "START", "label": "Start", "position": {"x": 100, "y": 100}, "data": {}},
            {"id": "rule-1", "type": "RULE", "label": "VIP Rule", "position": {"x": 100, "y": 250}, "data": {"ruleKey": "IS_VIP"}},
            {"id": "end-1", "type": "END", "label": "End", "position": {"x": 100, "y": 400}, "data": {}}
        ],
        "edges": [
            {"id": "e1", "source": "start-1", "target": "rule-1", "label": "go"},
            {"id": "e2", "source": "rule-1", "target": "end-1", "label": "done"}
        ],
        "metadata": {"zoom": 1.5}
    }
    
    # Save the design to v1
    updated_v1 = put_json(f"{BASE_URL}/versions/{v1_id}", custom_graph)
    print(f"Version 1 saved with {len(updated_v1['definition']['nodes'])} nodes and {len(updated_v1['definition']['edges'])} edges.")
    
    print("3. Creating a new draft version (v2) without sending a body...")
    # This simulates creating a draft in the backend when no body is passed, or verifying our backend clone logic
    created_v2 = post_json(f"{BASE_URL}/{wf_id}/versions", None)
    v2_id = created_v2["id"]
    v2_version = created_v2["version"]
    v2_nodes = created_v2["definition"]["nodes"]
    v2_edges = created_v2["definition"]["edges"]
    
    print(f"Version {v2_version} draft created with ID {v2_id}")
    print(f"Version {v2_version} contains {len(v2_nodes)} nodes and {len(v2_edges)} edges.")
    
    # Assert nodes and edges were copied correctly
    assert len(v2_nodes) == 3, f"Expected 3 nodes, got {len(v2_nodes)}"
    assert len(v2_edges) == 2, f"Expected 2 edges, got {len(v2_edges)}"
    assert v2_nodes[1]["label"] == "VIP Rule", "VIP Rule label was not cloned correctly"
    assert created_v2["definition"]["metadata"]["zoom"] == 1.5, "Metadata was not cloned correctly"
    
    print("SUCCESS: Draft structure was copied successfully in the backend!")

if __name__ == "__main__":
    main()
