import sys

# 1. CustomNode.tsx
file_path = 'c:\\Users\\hemant\\Desktop\\Projects\\state-machine-engine\\atlas-ui\\src\\components\\designer\\CustomNode.tsx'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Make canvas nodes extremely thin vertically
content = content.replace("p: 0.2, '&:last-child': { pb: 0.2 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 26", 
                          "p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18")
content = content.replace("width: 16", "width: 14")
content = content.replace("height: 16", "height: 14")

# Hide sub-labels so they don't break the thin layout (just comment them out via false &&)
content = content.replace("{data.ruleId && <Typography", "{false && <Typography")
content = content.replace("{data.bucketId && <Typography", "{false && <Typography")
content = content.replace("{data.childWorkflowKey && <Typography", "{false && <Typography")
content = content.replace("{(data.commandType || data.type) && <Typography", "{false && <Typography")
content = content.replace("{data.eventType && <Typography", "{false && <Typography")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)


# 2. NodeCatalog.tsx
file_path = 'c:\\Users\\hemant\\Desktop\\Projects\\state-machine-engine\\atlas-ui\\src\\components\\designer\\NodeCatalog.tsx'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Make drawer nodes extremely thin vertically
content = content.replace("p: 0.5, '&:last-child': { pb: 0.5 }, display: 'flex', gap: 1.5, alignItems: 'center', height: 40",
                          "p: 0, pl: 1, pr: 1, '&:last-child': { pb: 0 }, display: 'flex', gap: 1.0, alignItems: 'center', height: 24")
content = content.replace("height: 20", "height: 16")
content = content.replace("width: 20", "width: 16")
content = content.replace("fontSize: '12px'", "fontSize: '10px'")
content = content.replace("<Typography variant=\"caption\"", "{/* <Typography variant=\"caption\"")
content = content.replace("</Typography>\n              </Box>", "</Typography> */}\n              </Box>")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated successfully.")
