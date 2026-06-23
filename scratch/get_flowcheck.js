const http = require('http');

http.get('http://localhost:9091/api/workflows', (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    try {
      const json = JSON.parse(data);
      const flowcheckList = json.filter(w => w.key === 'flowcheck');
      console.log(`Found ${flowcheckList.length} matches for flowcheck key.`);
      
      // Let's print each version's details
      json.forEach(workflow => {
        if (workflow.key === 'flowcheck' || workflow.name === 'flowcheck') {
          console.log(`\n================ WORKFLOW key: ${workflow.key}, name: ${workflow.name} ================`);
          console.log(`ID: ${workflow.id}, Active Version: ${workflow.activeVersion}`);
          if (workflow.versions) {
            workflow.versions.forEach(v => {
              console.log(`  Version: ${v.version}, Status: ${v.status}, ID: ${v.id}`);
              const def = v.definition;
              if (def && def.nodes) {
                console.log(`    Nodes:`);
                def.nodes.forEach(n => {
                  console.log(`      - [${n.type}] id=${n.id}, label="${n.label}", data=${JSON.stringify(n.data)}`);
                });
              }
              if (def && def.edges) {
                console.log(`    Edges:`);
                def.edges.forEach(e => {
                  console.log(`      - source=${e.source} -> target=${e.target}, label="${e.label}", condition="${e.data?.condition || ''}"`);
                });
              }
            });
          }
        }
      });
    } catch (e) {
      console.error('Error parsing JSON:', e.message);
    }
  });
}).on('error', (err) => {
  console.error('Error:', err.message);
});
