import requests
import json

BASE_URL = "http://localhost:9091/api"

def test():
    rule_payload = {
        "ruleKey": "TEST_RULE",
        "name": "Test Rule",
        "description": "Test SpEL rule",
        "expression": "context['amount'] > 5000",
        "active": True
    }
    
    print("Registering rule...")
    res = requests.post(f"{BASE_URL}/rules", json=rule_payload)
    print("POST Response status:", res.status_code)
    print("POST Response body:", res.text)
    
    print("\nGetting all rules...")
    res = requests.get(f"{BASE_URL}/rules")
    print("GET Response status:", res.status_code)
    print("GET Response body:", res.text)

if __name__ == "__main__":
    test()
