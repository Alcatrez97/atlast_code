const http = require('http');

http.get('http://localhost:9091/api/executions', (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    try {
      const json = JSON.parse(data);
      console.log(`Found ${json.length} executions.`);
      // Let's print the latest 2 executions
      json.slice(0, 2).forEach((exec, idx) => {
        console.log(`\n--- Execution #${idx+1} ---`);
        console.log(`ID: ${exec.id}, Instance ID: ${exec.instanceId}`);
        console.log(`Workflow Key: ${exec.workflowKey}, Version: ${exec.versionNumber}`);
        console.log(`Status: ${exec.status}, Context ID: ${exec.contextId}`);
        console.log(`Input Context: ${JSON.stringify(exec.inputContext)}`);
        if (exec.executionTrace) {
          console.log(`Trace steps:`);
          exec.executionTrace.forEach(step => {
            console.log(`  - Step ${step.stepIndex}: [${step.nodeType}] ${step.label} (${step.status}) -> Edge: ${step.edgeTaken}`);
            console.log(`    Notes: ${step.notes}`);
            console.log(`    Result: ${step.expressionResult}`);
          });
        }
      });
    } catch (e) {
      console.error('Error parsing JSON:', e.message);
    }
  });
}).on('error', (err) => {
  console.error('Error:', err.message);
});
