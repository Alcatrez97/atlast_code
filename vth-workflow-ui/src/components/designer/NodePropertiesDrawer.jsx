import React, { useState, useEffect } from 'react';
import { Drawer, Box, Typography, IconButton, Divider, TextField, Button, Chip, CircularProgress, Alert, FormControl, InputLabel, Select, MenuItem, Tooltip, Paper, Switch, FormControlLabel } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import SaveIcon from '@mui/icons-material/Save';
import InfoIcon from '@mui/icons-material/Info';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { useWorkflowStore } from '../../store/workflowStore.js';
import './designer.css';
const NODE_TYPE_COLORS = {
  START: '#10b981', END: '#ef4444', RULE: '#6366f1',
  DECISION: '#f59e0b', BUCKET: '#a855f7', TIMER: '#14b8a6',
  PARALLEL: '#f97316', JOIN: '#f97316', SUB_WORKFLOW: '#3b82f6',
  COMMAND: '#38bdf8', WAIT_EVENT: '#f59e0b'
};
const STEP_STATUS_COLORS = {
  ENTERED: '#6366f1', EVALUATED: '#f59e0b', ROUTED: '#14b8a6',
  COMPLETED: '#10b981', FAILED: '#ef4444', SKIPPED: '#6b7280',
  WAITING: '#f59e0b'
};
const isValidJson = (str) => {
  if (!str || !str.trim()) return true;
  try {
    JSON.parse(str);
    return true;
  } catch (e) {
    return false;
  }
};

export const NodePropertiesDrawer = ({ open, node, isReadOnly, traceStep, onClose, onSaveNode, onDeleteNode }) => {
  const [label, setLabel] = useState('');
  const [expression, setExpression] = useState('');
  const [bucketId, setBucketId] = useState('');
  const [dependencyBuckets, setDependencyBuckets] = useState([]);
  const [ruleId, setRuleId] = useState('');
  const [delayMs, setDelayMs] = useState('');
  const [decisionField, setDecisionField] = useState('');
  const [decisionType, setDecisionType] = useState('FIELD');
  const [childWorkflowKey, setChildWorkflowKey] = useState('');
  const [inputMappingStr, setInputMappingStr] = useState('{}');
  const [outputMappingStr, setOutputMappingStr] = useState('{}');
  const [payloadMappingStr, setPayloadMappingStr] = useState('{}');
  const [commandType, setCommandType] = useState('');
  const [executionMode, setExecutionMode] = useState('SYNC');
  const [eventType, setEventType] = useState('');
  const [routesStr, setRoutesStr] = useState('[]');
  const [defaultRoute, setDefaultRoute] = useState('');
  const [formStatus, setFormStatus] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [joinType, setJoinType] = useState('AND');
  const [businessEligibilityRule, setBusinessEligibilityRule] = useState('');
  const [orientation, setOrientation] = useState('vertical');
  const [disabledBehavior, setDisabledBehavior] = useState('SKIP_ACCEPT');
  const [isIdempotent, setIsIdempotent] = useState(true);
  const { selectedWorkflow } = useWorkflowStore();
  const [schemaFields, setSchemaFields] = useState([]);
  const [registeredBuckets, setRegisteredBuckets] = useState([]);
  const [registeredRules, setRegisteredRules] = useState([]);
  const [registeredWorkflows, setRegisteredWorkflows] = useState([]);
  const [registeredEvents, setRegisteredEvents] = useState([]);
  const [availableCommands, setAvailableCommands] = useState([]);
  const [registeredIntegrations, setRegisteredIntegrations] = useState([]);

  // REST / External System specific states
  const [integrationKey, setIntegrationKey] = useState('');
  const [restUrl, setRestUrl] = useState('');
  const [restMethod, setRestMethod] = useState('GET');
  const [restHeadersStr, setRestHeadersStr] = useState('{}');
  const [restTimeout, setRestTimeout] = useState(10);
  const [commandParams, setCommandParams] = useState({});

  // Fetch schema fields, registered buckets, and registered rules when drawer opens
  useEffect(() => {
    if (!open || !selectedWorkflow)
      return;
    fetch(`/api/context-schemas/${selectedWorkflow.key}`)
      .then(res => res.ok ? res.json() : null)
      .then(data => {
        if (data && data.fields) {
          setSchemaFields(data.fields);
        }
        else {
          setSchemaFields([]);
        }
      })
      .catch(() => setSchemaFields([]));
    fetch('/api/buckets')
      .then(res => res.ok ? res.json() : [])
      .then(data => setRegisteredBuckets(data))
      .catch(() => setRegisteredBuckets([]));
    fetch('/api/rules')
      .then(res => res.ok ? res.json() : [])
      .then(data => setRegisteredRules(data))
      .catch(() => setRegisteredRules([]));
    fetch('/api/workflows')
      .then(res => res.ok ? res.json() : [])
      .then(data => setRegisteredWorkflows(data))
      .catch(() => setRegisteredWorkflows([]));
    fetch('/api/event-definitions')
      .then(res => res.ok ? res.json() : [])
      .then(data => setRegisteredEvents(data))
      .catch(() => setRegisteredEvents([]));
    fetch('/api/commands')
      .then(res => res.ok ? res.json() : [])
      .then(data => setAvailableCommands(data))
      .catch(() => setAvailableCommands([]));
    fetch('/api/integrations')
      .then(res => res.ok ? res.json() : [])
      .then(data => setRegisteredIntegrations(data))
      .catch(() => setRegisteredIntegrations([]));
  }, [open, selectedWorkflow]);
  // Populate fields when node changes
  useEffect(() => {
    if (node) {
      setLabel(node.label || '');
      setExpression(node.data?.expression || '');
      setBucketId(node.data?.bucketId || '');
      setDependencyBuckets(node.data?.dependencyBuckets || []);
      setRuleId(node.data?.ruleId || node.data?.id || '');
      setDelayMs(node.data?.delayMs || '');
      setDecisionField(node.data?.decisionField || '');
      setDecisionType(node.data?.expression ? 'EXPRESSION' : 'FIELD');
      setChildWorkflowKey(node.data?.childWorkflowKey || '');
      if ((node.data?.commandType || node.data?.type) === 'EMIT_EVENT' || (node.data?.commandType || node.data?.type) === 'PUBLISH_EVENT') {
        setInputMappingStr(node.data?.payloadMapping ? JSON.stringify(node.data.payloadMapping, null, 2) : '{}');
      }
      else {
        setInputMappingStr(node.data?.inputMapping ? JSON.stringify(node.data.inputMapping, null, 2) : '{}');
      }
      setOutputMappingStr(node.data?.outputMapping ? JSON.stringify(node.data.outputMapping, null, 2) : '{}');
      setCommandType(node.data?.commandType || node.data?.type || '');
      setEventType(node.data?.eventType || node.data?.eventKey || '');
      setPayloadMappingStr(node.data?.payloadMapping ? JSON.stringify(node.data.payloadMapping, null, 2) : '{}');
      setRoutesStr(node.data?.routes ? JSON.stringify(node.data.routes, null, 2) : '[]');
      setDefaultRoute(node.data?.defaultRoute || '');
      setFormStatus(node.data?.formStatus || node.data?.status || '');
      setExecutionMode(node.data?.executionMode || 'SYNC');
      setJoinType(node.data?.joinType || 'AND');
      setBusinessEligibilityRule(node.data?.businessEligibilityRule || node.data?.activationCondition || '');
      setOrientation(node.data?.orientation || 'vertical');
      setDisabledBehavior(node.data?.disabledBehavior || 'SKIP_ACCEPT');

      // Initialize REST / External System properties
      setIntegrationKey(node.data?.integrationKey || '');
      setRestUrl(node.data?.url || node.data?.endpointUrl || '');
      setRestMethod(node.data?.method || 'GET');
      setRestHeadersStr(node.data?.headers ? (typeof node.data.headers === 'string' ? node.data.headers : JSON.stringify(node.data.headers, null, 2)) : '{}');
      setRestTimeout(node.data?.timeoutSeconds || node.data?.timeout || 10);

      // Initialize dynamic command params
      setCommandParams(node.data || {});

      // Determine idempotency from node data or fallback to safe node-type defaults
      if (node.data?.isIdempotent !== undefined) {
        setIsIdempotent(Boolean(node.data.isIdempotent));
      } else {
        const type = node.type?.toUpperCase() || '';
        const defaultIdempotent = !['COMMAND', 'WAIT_EVENT', 'BUCKET', 'SUB_WORKFLOW'].includes(type);
        setIsIdempotent(defaultIdempotent);
      }

      setSaved(false);
    }
  }, [node]);
  const nodeType = node?.type?.toUpperCase() || '';
  const accentColor = NODE_TYPE_COLORS[nodeType] || '#6366f1';
  const handleSave = async () => {
    if (!node || !onSaveNode)
      return;
    setIsSaving(true);
    try {
      const updatedData = {
        ...node.data,
        isIdempotent,
        joinType,
        businessEligibilityRule,
        orientation
      };
      if (nodeType === 'RULE') {
        updatedData.expression = expression;
        updatedData.ruleId = ruleId;
      }
      else if (nodeType === 'DECISION') {
        if (decisionType === 'EXPRESSION') {
          updatedData.expression = expression;
          delete updatedData.decisionField;
        }
        else {
          updatedData.decisionField = decisionField;
          delete updatedData.expression;
        }
      }
      else if (nodeType === 'BUCKET') {
        updatedData.bucketId = bucketId;
        updatedData.dependencyBuckets = dependencyBuckets;
        updatedData.disabledBehavior = disabledBehavior;
      }
      else if (nodeType === 'TIMER') {
        updatedData.delayMs = delayMs;
      }
      else if (nodeType === 'SUB_WORKFLOW') {
        updatedData.childWorkflowKey = childWorkflowKey;
        try {
          updatedData.inputMapping = JSON.parse(inputMappingStr || '{}');
        }
        catch (e) {
          alert('Invalid JSON in Input Mapping');
          setIsSaving(false);
          return;
        }
        try {
          updatedData.outputMapping = JSON.parse(outputMappingStr || '{}');
        }
        catch (e) {
          alert('Invalid JSON in Output Mapping');
          setIsSaving(false);
          return;
        }
      }
      else if (nodeType === 'COMMAND') {
        updatedData.commandType = commandType;
        updatedData.executionMode = executionMode;
        if (commandType === 'REST' || commandType === 'CALL_EXTERNAL_SYSTEM' || commandType === 'HTTP') {
          updatedData.integrationKey = integrationKey;
          updatedData.url = restUrl;
          updatedData.method = restMethod;
          try {
            updatedData.headers = JSON.parse(restHeadersStr || '{}');
          } catch (e) {
            alert('Invalid JSON in Request Headers');
            setIsSaving(false);
            return;
          }
          updatedData.timeoutSeconds = Number(restTimeout) || 10;
          try {
            updatedData.inputMapping = JSON.parse(inputMappingStr || '{}');
          } catch (e) {
            alert('Invalid JSON in Request Body / Input Mapping');
            setIsSaving(false);
            return;
          }
          try {
            updatedData.outputMapping = JSON.parse(outputMappingStr || '{}');
          } catch (e) {
            alert('Invalid JSON in Output Mapping');
            setIsSaving(false);
            return;
          }
        }
        else if (commandType === 'CREATE_BUCKET') {
          updatedData.bucketId = bucketId;
          updatedData.dependencyBuckets = dependencyBuckets;
        }
        else if (commandType === 'UPDATE_FORM_STATUS') {
          updatedData.formStatus = formStatus;
        }
        else if (commandType === 'START_CHILD_WORKFLOW' || commandType === 'START_WORKFLOW') {
          updatedData.childWorkflowKey = childWorkflowKey;
          try {
            updatedData.inputMapping = JSON.parse(inputMappingStr || '{}');
          }
          catch (e) {
            alert('Invalid JSON in Input Mapping');
            setIsSaving(false);
            return;
          }
          try {
            updatedData.outputMapping = JSON.parse(outputMappingStr || '{}');
          }
          catch (e) {
            alert('Invalid JSON in Output Mapping');
            setIsSaving(false);
            return;
          }
        }
        else if (commandType === 'EMIT_EVENT' || commandType === 'PUBLISH_EVENT') {
          updatedData.eventType = eventType;
          updatedData.eventKey = eventType;
          try {
            updatedData.payloadMapping = JSON.parse(inputMappingStr || '{}');
          }
          catch (e) {
            alert('Invalid JSON in Payload Mapping');
            setIsSaving(false);
            return;
          }
        }
        else {
          // Dynamic command parameters (e.g. FINALIZE_CAF_SUBMISSION, MQ, etc.)
          Object.keys(commandParams).forEach(k => {
            if (!k.startsWith('_') && k !== 'commandType' && k !== 'executionMode') {
              let val = commandParams[k];
              if (typeof val === 'string' && (val.trim().startsWith('{') || val.trim().startsWith('['))) {
                try { val = JSON.parse(val); } catch (_) {}
              }
              updatedData[k] = val;
            }
          });
        }
      }
      else if (nodeType === 'WAIT_EVENT') {
        updatedData.eventType = eventType;
        try {
          updatedData.payloadMapping = JSON.parse(payloadMappingStr || '{}');
        }
        catch (e) {
          alert('Invalid JSON in Selective Payload Mapping');
          setIsSaving(false);
          return;
        }
        try {
          updatedData.routes = JSON.parse(routesStr || '[]');
        }
        catch (e) {
          alert('Invalid JSON in Routes configuration');
          setIsSaving(false);
          return;
        }
        updatedData.defaultRoute = defaultRoute;
      }
      await onSaveNode(node.id, { label, data: updatedData });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    }
    finally {
      setIsSaving(false);
    }
  };
  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      variant="persistent"
      slotProps={{ paper: { className: 'designer-drawer-paper' } }}
    >
      {node && (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header */}
          <Box className="designer-drawer-header">
            <Box className="designer-drawer-header-icon" sx={{ bgcolor: accentColor + '20', border: `1px solid ${accentColor}40` }}>
              {isReadOnly ? <InfoIcon sx={{ fontSize: 16, color: accentColor }} /> : <EditIcon sx={{ fontSize: 16, color: accentColor }} />}
            </Box>
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                {node.label || nodeType}
              </Typography>
              <Box sx={{ display: 'flex', gap: 0.5, mt: 0.25, alignItems: 'center' }}>
                <Chip label={nodeType} size="small" sx={{ height: 16, fontSize: '8px', fontWeight: 800, bgcolor: accentColor + '20', color: accentColor, border: 'none' }} />
                <Chip
                  label={isIdempotent ? "IDEMPOTENT" : "STATEFUL"}
                  size="small"
                  sx={{
                    height: 16,
                    fontSize: '8px',
                    fontWeight: 800,
                    bgcolor: isIdempotent ? 'rgba(16,185,129,0.15)' : 'rgba(245,158,11,0.15)',
                    color: isIdempotent ? '#10b981' : '#f59e0b',
                    border: 'none'
                  }}
                />
                {isReadOnly && <Chip label="READ ONLY" size="small" sx={{ height: 16, fontSize: '8px', bgcolor: 'rgba(255,255,255,0.05)', color: 'text.secondary', border: 'none' }} />}
              </Box>
            </Box>
            <IconButton size="small" onClick={onClose}><CloseIcon fontSize="small" /></IconButton>
          </Box>

          <Box className="designer-drawer-body">
            {/* Trace step info (trace/read-only mode) */}
            {traceStep && (<Box className="designer-drawer-trace-section">
                <Typography className="designer-drawer-section-title" variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 1, display: 'block', mb: 1 }}>
                  EXECUTION TRACE
                </Typography>
                <Box className="designer-drawer-trace-card" sx={{
                p: 1.5, borderRadius: 2,
                bgcolor: `${STEP_STATUS_COLORS[traceStep.status] || '#6366f1'}10`,
                border: `1px solid ${STEP_STATUS_COLORS[traceStep.status] || '#6366f1'}30`
              }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <Chip label={traceStep.status} size="small" sx={{ height: 18, fontSize: '9px', fontWeight: 800, bgcolor: `${STEP_STATUS_COLORS[traceStep.status]}20`, color: STEP_STATUS_COLORS[traceStep.status] }} />
                  {traceStep.durationMs !== undefined && (<Typography variant="caption" >{traceStep.durationMs}ms</Typography>)}
                </Box>
                <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.5 }}>
                  {traceStep.notes}
                </Typography>
                {traceStep.expression && (<Box sx={{ mt: 1.5 }}>
                  <Typography className="designer-drawer-section-title" variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>EXPRESSION</Typography>
                  <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#818cf8', display: 'block', wordBreak: 'break-all' }}>
                    {traceStep.expression}
                  </Typography>
                </Box>)}
                {traceStep.expressionResult !== undefined && traceStep.expressionResult !== null && (<Box sx={{ mt: 1 }}>
                  <Typography className="designer-drawer-section-title" variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>RESULT</Typography>
                  <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#10b981' }}>
                    {JSON.stringify(traceStep.expressionResult)}
                  </Typography>
                </Box>)}
              </Box>
              <Divider sx={{ mt: 2 }} />
            </Box>)}

            {/* Node ID */}
            <Box className="designer-drawer-properties-section">
              <Typography className="designer-drawer-section-title" variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 1, display: 'block', mb: 1 }}>
                NODE PROPERTIES
              </Typography>
              <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#6b7280', display: 'block', mb: 1.5, wordBreak: 'break-all' }}>
                ID: {node.id}
              </Typography>

              {/* Label */}
              <TextField fullWidth size="small" label="Label" value={label} onChange={(e) => setLabel(e.target.value)} disabled={isReadOnly} sx={{ mb: 1.5 }} />

              {/* Orientation */}
              <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                <InputLabel sx={{ fontSize: '12px' }}>Connection Orientation</InputLabel>
                <Select label="Connection Orientation" value={orientation} onChange={(e) => setOrientation(e.target.value)} disabled={isReadOnly} sx={{ fontSize: '12px' }}>
                  <MenuItem value="vertical" sx={{ fontSize: '12px' }}>Vertical (Top/Bottom Handles)</MenuItem>
                  <MenuItem value="horizontal" sx={{ fontSize: '12px' }}>Horizontal (Left/Right Handles)</MenuItem>
                </Select>
              </FormControl>

              {/* Join Type (for non-START nodes) */}
              {nodeType !== 'START' && (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                <InputLabel sx={{ fontSize: '12px' }}>Join Type (Structural)</InputLabel>
                <Select label="Join Type (Structural)" value={joinType} onChange={(e) => setJoinType(e.target.value)} disabled={isReadOnly} sx={{ fontSize: '12px' }}>
                  <MenuItem value="AND" sx={{ fontSize: '12px' }}>AND (All incoming paths must match)</MenuItem>
                  <MenuItem value="OR" sx={{ fontSize: '12px' }}>OR (Any incoming path matches)</MenuItem>
                </Select>
              </FormControl>)}

              {/* Business Eligibility Rule (for non-START nodes) */}
              {nodeType !== 'START' && (<TextField fullWidth size="small" label="Business Eligibility Rule (SpEL)" value={businessEligibilityRule} onChange={(e) => setBusinessEligibilityRule(e.target.value)} disabled={isReadOnly} multiline rows={2} placeholder="context.amount > 50000" helperText={!isReadOnly ? "Only evaluates business variables. E.g. context.requiresA2 == true" : undefined} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '12px' } } }} sx={{ mb: 1.5 }} />)}

              {/* Idempotent Toggle */}
              <FormControlLabel
                control={
                  <Switch
                    checked={isIdempotent}
                    onChange={(e) => setIsIdempotent(e.target.checked)}
                    disabled={isReadOnly}
                    size="small"
                  />
                }
                label={<Typography sx={{ fontSize: '12px', fontWeight: 600 }}>Idempotent</Typography>}
                sx={{ mb: 1.5, ml: 0, width: '100%', display: 'flex', justifyContent: 'space-between' }}
                labelPlacement="start"
              />

              {/* RULE fields */}
              {nodeType === 'RULE' && (<>
                {/* Registry Rule selector */}
                {!isReadOnly && (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Reference Registry Rule</InputLabel>
                  <Select label="Reference Registry Rule" value={ruleId} onChange={(e) => {
                    const val = e.target.value;
                    setRuleId(val);
                    if (val) {
                      setExpression(''); // clear inline expression when rule reference is selected
                    }
                  }} sx={{ fontSize: '12px' }}>
                    <MenuItem value="" sx={{ fontSize: '12px' }}>
                      <em>None (Inline SpEL Expression)</em>
                    </MenuItem>
                    {registeredRules.map(r => (<MenuItem key={r.id} value={r.ruleKey} sx={{ fontSize: '12px' }}>
                      {r.name} ({r.ruleKey}) {!r.active && '(INACTIVE)'}
                    </MenuItem>))}
                  </Select>
                </FormControl>)}

                {ruleId ? ((() => {
                  const matched = registeredRules.find(r => r.ruleKey === ruleId);
                  if (matched) {
                    return (<Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.15)', mb: 1.5 }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: '#6366f1', display: 'block', mb: 0.5 }}>
                        REFERENCED RULE DETAILS
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 0.25 }}>
                        Name: <strong>{matched.name}</strong>
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 0.25 }}>
                        Key: <code>{matched.ruleKey}</code>
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 0.25 }}>
                        RuleId: <code>{matched.id}</code>
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 0.25, fontFamily: 'monospace', fontSize: '10px' }}>
                        Expr: {matched.expression}
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: matched.active ? '#22c55e' : '#ef4444', fontWeight: 800 }}>
                        Status: {matched.active ? 'Active' : 'Inactive (WILL FAIL FLOW!)'}
                      </Typography>
                    </Box>);
                  }
                  return (<Alert severity="warning" sx={{ mb: 1.5, py: 0.5, borderRadius: 1.5, fontSize: '11px' }}>
                    Warning: Referenced rule key '<code>{ruleId}</code>' not found in registry!
                  </Alert>);
                })()) : (<>
                  {schemaFields.length > 0 && !isReadOnly && (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                    <InputLabel sx={{ fontSize: '12px' }}>Insert Known Context Field</InputLabel>
                    <Select label="Insert Known Context Field" value="" onChange={(e) => {
                      const val = e.target.value;
                      if (val) {
                        setExpression(prev => prev + `context['${val}']`);
                      }
                    }} sx={{ fontSize: '12px' }}>
                      {schemaFields.map(f => (<MenuItem key={f.fieldKey} value={f.fieldKey} sx={{ fontSize: '12px' }}>
                        {f.displayName} ({f.fieldKey} · {f.fieldType})
                      </MenuItem>))}
                    </Select>
                  </FormControl>)}
                  <TextField fullWidth size="small" label="SpEL Expression" value={expression} onChange={(e) => setExpression(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder="context['amount'] > 5000" helperText={!isReadOnly ? "Use context.field or context['field'] to access payload values" : undefined} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '12px' } } }} sx={{ mb: 1.5 }} />
                </>)}
              </>)}

              {/* DECISION fields */}
              {nodeType === 'DECISION' && (<>
                {!isReadOnly && (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Decision Source</InputLabel>
                  <Select label="Decision Source" value={decisionType} onChange={(e) => setDecisionType(e.target.value)} sx={{ fontSize: '12px' }}>
                    <MenuItem value="FIELD" sx={{ fontSize: '12px' }}>Context Field Key</MenuItem>
                    <MenuItem value="EXPRESSION" sx={{ fontSize: '12px' }}>Custom SpEL Expression</MenuItem>
                  </Select>
                </FormControl>)}

                {isReadOnly && (<Chip label={decisionType === 'EXPRESSION' ? 'SpEL Expression Mode' : 'Context Field Mode'} size="small" sx={{ mb: 1.5, height: 20, fontSize: '9px', fontWeight: 800, bgcolor: 'rgba(255,255,255,0.05)', color: 'text.secondary', border: 'none' }} />)}

                {decisionType === 'FIELD' ? (schemaFields.length > 0 && !isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Decision Field Key</InputLabel>
                  <Select label="Decision Field Key" value={decisionField} onChange={(e) => setDecisionField(e.target.value)} sx={{ fontSize: '12px' }}>
                    {schemaFields.map(f => (<MenuItem key={f.fieldKey} value={f.fieldKey} sx={{ fontSize: '12px' }}>
                      {f.displayName} ({f.fieldKey})
                    </MenuItem>))}
                  </Select>
                </FormControl>) : (<TextField fullWidth size="small" label="Decision Field Key" value={decisionField} onChange={(e) => setDecisionField(e.target.value)} disabled={isReadOnly} placeholder="status" helperText={!isReadOnly ? "Context field name to evaluate for routing (e.g. 'status')" : undefined} />)) : (<>
                  {schemaFields.length > 0 && !isReadOnly && (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                    <InputLabel sx={{ fontSize: '12px' }}>Insert Known Context Field</InputLabel>
                    <Select label="Insert Known Context Field" value="" onChange={(e) => {
                      const val = e.target.value;
                      if (val) {
                        setExpression(prev => prev + `context['${val}']`);
                      }
                    }} sx={{ fontSize: '12px' }}>
                      {schemaFields.map(f => (<MenuItem key={f.fieldKey} value={f.fieldKey} sx={{ fontSize: '12px' }}>
                        {f.displayName} ({f.fieldKey} · {f.fieldType})
                      </MenuItem>))}
                    </Select>
                  </FormControl>)}
                  <TextField fullWidth size="small" label="SpEL Expression" value={expression} onChange={(e) => setExpression(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder="context['amount'] > 10000 ? 'HIGH' : 'LOW'" helperText={!isReadOnly ? "SpEL statement that evaluates to an outcome matching outgoing edge conditions" : undefined} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '12px' } } }} sx={{ mb: 1.5 }} />
                </>)}
              </>)}

              {/* BUCKET fields */}
              {nodeType === 'BUCKET' && (<>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 0.5 }}>
                    BUCKET SETTINGS
                  </Typography>
                  <Tooltip title={<Box sx={{ p: 1, maxWidth: 260 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#a855f7', mb: 0.5 }}>
                      Bucket Node Configuration
                    </Typography>
                    <Typography variant="caption" sx={{ display: 'block', mb: 1, color: '#cbd5e1', lineHeight: 1.4 }}>
                      A Bucket task suspends execution and waits for an external form status update.
                    </Typography>
                    <Typography variant="caption" sx={{ display: 'block', mb: 0.5, fontWeight: 700, color: '#fff' }}>
                      Configure Downstream Edges:
                    </Typography>
                    <Typography variant="caption" sx={{ display: 'block', mb: 0.25, color: '#a855f7', fontFamily: 'monospace' }}>
                      • Pending State: &lt;bucketId&gt; Pending
                    </Typography>
                    <Typography variant="caption" sx={{ display: 'block', mb: 0.25, color: '#10b981', fontFamily: 'monospace' }}>
                      • Approve Edge: context.form_status == '&lt;bucketId&gt;Accept'
                    </Typography>
                    <Typography variant="caption" sx={{ display: 'block', color: '#ef4444', fontFamily: 'monospace' }}>
                      • Reject Edge: context.form_status == '&lt;bucketId&gt;Reject'
                    </Typography>
                  </Box>} arrow placement="left">
                    <IconButton size="small" sx={{ color: '#a855f7', p: 0.5 }}>
                      <InfoIcon sx={{ fontSize: '16px' }} />
                    </IconButton>
                  </Tooltip>
                </Box>

                {registeredBuckets.length > 0 && !isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Outcome Bucket</InputLabel>
                  <Select label="Outcome Bucket" value={bucketId} onChange={(e) => setBucketId(e.target.value)} sx={{ fontSize: '12px' }}>
                    {registeredBuckets.map(b => (<MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                      {b.name} ({b.bucketId})
                    </MenuItem>))}
                  </Select>
                </FormControl>) : (<TextField fullWidth size="small" label="Bucket ID" value={bucketId} onChange={(e) => setBucketId(e.target.value)} disabled={isReadOnly} placeholder="BCK_001" sx={{ mb: 1.5 }} />)}

                {/* Dependency Buckets selection */}
                {!isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Dependency Buckets</InputLabel>
                  <Select multiple label="Dependency Buckets" value={dependencyBuckets} onChange={(e) => {
                    const val = e.target.value;
                    setDependencyBuckets(typeof val === 'string' ? val.split(',') : (val));
                  }} renderValue={(selected) => (<Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected).map((value) => (<Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />))}
                  </Box>)} sx={{ fontSize: '12px' }}>
                    {registeredBuckets
                      .filter(b => b.bucketId !== bucketId)
                      .map(b => (<MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                        {b.name} ({b.bucketId})
                      </MenuItem>))}
                  </Select>
                </FormControl>) : (dependencyBuckets.length > 0 && (<Box sx={{ mb: 1.5 }}>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>
                    DEPENDENCY BUCKETS
                  </Typography>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {dependencyBuckets.map((value) => (<Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />))}
                  </Box>
                </Box>))}

                {/* Disabled Behavior (Routing option when skipped) */}
                <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Disabled Behavior</InputLabel>
                  <Select label="Disabled Behavior" value={disabledBehavior} onChange={(e) => setDisabledBehavior(e.target.value)} disabled={isReadOnly} sx={{ fontSize: '12px' }}>
                    <MenuItem value="SKIP_ACCEPT" sx={{ fontSize: '12px' }}>SKIP_ACCEPT (Follow Accept edge when disabled)</MenuItem>
                    <MenuItem value="SKIP_REJECT" sx={{ fontSize: '12px' }}>SKIP_REJECT (Follow Reject edge when disabled)</MenuItem>
                    <MenuItem value="SKIP_THROUGH" sx={{ fontSize: '12px' }}>SKIP_THROUGH (Follow default/unconditional edge)</MenuItem>
                  </Select>
                </FormControl>

                {(() => {
                  const matched = registeredBuckets.find(b => b.bucketId === bucketId);
                  if (matched) {
                    return (<Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(168,85,247,0.06)', border: '1px solid rgba(168,85,247,0.15)', mt: 1 }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: '#a855f7', display: 'block', mb: 0.5 }}>
                        BUCKET REGISTRY INFO
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 0.25 }}>
                        Priority: <strong>{matched.priority}</strong>
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 0.25 }}>
                        SLA Target: <strong>{matched.slaHours ? `${matched.slaHours} hours` : 'None'}</strong>
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                        Owner Group: <strong>{matched.ownerGroup || 'None'}</strong>
                      </Typography>
                    </Box>);
                  }
                  return null;
                })()}
              </>)}

              {/* TIMER fields */}
              {nodeType === 'TIMER' && (<TextField fullWidth size="small" label="Delay (ms)" value={delayMs} onChange={(e) => setDelayMs(e.target.value)} disabled={isReadOnly} type="number" placeholder="5000" />)}

              {nodeType === 'COMMAND' && (<>
                <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Command Type</InputLabel>
                  <Select
                    label="Command Type"
                    value={commandType}
                    onChange={(e) => {
                      const newType = e.target.value;
                      setCommandType(newType);
                      const meta = availableCommands.find(c => c.type === newType);
                      if (meta && meta.parameters) {
                        const defaults = {};
                        meta.parameters.forEach(p => {
                          if (p.defaultValue !== undefined && p.defaultValue !== null) {
                            defaults[p.name] = p.defaultValue;
                          }
                        });
                        setCommandParams(prev => ({ ...defaults, ...prev }));
                      }
                    }}
                    disabled={isReadOnly}
                    sx={{ fontSize: '12px' }}
                  >
                    {availableCommands.length > 0 ? (
                      availableCommands.map(cmd => (
                        <MenuItem key={cmd.type} value={cmd.type} sx={{ fontSize: '12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span>{cmd.displayName} ({cmd.type})</span>
                          <Chip label={cmd.category || 'General'} size="small" sx={{ height: 16, fontSize: '9px', ml: 1, opacity: 0.8 }} />
                        </MenuItem>
                      ))
                    ) : (
                      [
                        { type: 'REST', name: 'Call External REST API', cat: 'Integration' },
                        { type: 'FINALIZE_CAF_SUBMISSION', name: 'Finalize CAF Golden Record', cat: 'Lifecycle' },
                        { type: 'CREATE_BUCKET', name: 'Create Work Bucket', cat: 'Lifecycle' },
                        { type: 'UPDATE_FORM_STATUS', name: 'Update Form Status', cat: 'Lifecycle' },
                        { type: 'START_CHILD_WORKFLOW', name: 'Start Child Workflow', cat: 'Workflow' },
                        { type: 'EMIT_EVENT', name: 'Emit Domain Event', cat: 'Event' },
                        { type: 'MQ', name: 'Publish to Kafka / MQ', cat: 'Messaging' },
                        { type: 'CALL_EXTERNAL_SYSTEM', name: 'Call External System', cat: 'Integration' }
                      ].map(cmd => (
                        <MenuItem key={cmd.type} value={cmd.type} sx={{ fontSize: '12px' }}>
                          {cmd.name} ({cmd.type})
                        </MenuItem>
                      ))
                    )}
                  </Select>
                </FormControl>

                {/* Command description banner */}
                {(() => {
                  const meta = availableCommands.find(c => c.type === commandType);
                  if (meta && meta.description) {
                    return (
                      <Alert severity="info" sx={{ py: 0.25, px: 1, mb: 1.5, fontSize: '11px', '& .MuiAlert-icon': { fontSize: 16 } }}>
                        {meta.description}
                      </Alert>
                    );
                  }
                  return null;
                })()}

                <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Execution Mode</InputLabel>
                  <Select label="Execution Mode" value={executionMode} onChange={(e) => setExecutionMode(e.target.value)} disabled={isReadOnly} sx={{ fontSize: '12px' }}>
                    <MenuItem value="SYNC" sx={{ fontSize: '12px' }}>Synchronous (SYNC)</MenuItem>
                    <MenuItem value="ASYNC" sx={{ fontSize: '12px' }}>Asynchronous (ASYNC)</MenuItem>
                  </Select>
                </FormControl>


                {/* 1. REST / External System API Configuration */}
                {(commandType === 'REST' || commandType === 'CALL_EXTERNAL_SYSTEM' || commandType === 'HTTP') && (<>
                  <Box sx={{ p: 1.5, mb: 1.5, borderRadius: 1.5, border: '1px solid rgba(56, 189, 248, 0.25)', bgcolor: 'rgba(56, 189, 248, 0.03)' }}>
                    <Typography variant="caption" sx={{ fontWeight: 800, color: '#38bdf8', display: 'block', mb: 1, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                      HTTP REST / External System Settings
                    </Typography>

                    {/* Pre-configured Integration Selector */}
                    <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                      <InputLabel sx={{ fontSize: '12px' }}>Integration Registry Profile</InputLabel>
                      <Select
                        label="Integration Registry Profile"
                        value={integrationKey}
                        onChange={(e) => {
                          const val = e.target.value;
                          setIntegrationKey(val);
                          const matched = registeredIntegrations.find(i => i.integrationKey === val);
                          if (matched) {
                            if (!restUrl || restUrl === '') setRestUrl(matched.endpointUrl || '');
                            if (matched.method) setRestMethod(matched.method.toUpperCase());
                            if (matched.headersJson && matched.headersJson !== '{}') setRestHeadersStr(matched.headersJson);
                            if (matched.timeoutMs) setRestTimeout(Math.max(1, Math.round(matched.timeoutMs / 1000)));
                          }
                        }}
                        disabled={isReadOnly}
                        sx={{ fontSize: '12px' }}
                      >
                        <MenuItem value="" sx={{ fontSize: '12px', fontStyle: 'italic', color: 'text.secondary' }}>
                          -- Custom Inline Endpoint (No Registry Profile) --
                        </MenuItem>
                        {registeredIntegrations.map(intg => (
                          <MenuItem key={intg.id || intg.integrationKey} value={intg.integrationKey} sx={{ fontSize: '12px' }}>
                            {intg.name} ({intg.integrationKey}) [{intg.method || 'GET'}]
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>

                    {/* Integration Profile Info preview if selected */}
                    {(() => {
                      const matched = registeredIntegrations.find(i => i.integrationKey === integrationKey);
                      if (matched) {
                        return (
                          <Box sx={{ p: 1, mb: 1.5, borderRadius: 1, bgcolor: 'rgba(255, 255, 255, 0.04)', border: '1px dashed rgba(255, 255, 255, 0.15)' }}>
                            <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                              Default Method: <strong>{matched.method}</strong> | Timeout: <strong>{matched.timeoutMs}ms</strong>
                            </Typography>
                            <Typography variant="caption" sx={{ display: 'block', color: '#38bdf8', fontFamily: 'monospace', fontSize: '10px', wordBreak: 'break-all' }}>
                              {matched.endpointUrl}
                            </Typography>
                          </Box>
                        );
                      }
                      return null;
                    })()}

                    {/* HTTP Method & Timeout row */}
                    <Box sx={{ display: 'flex', gap: 1, mb: 1.5 }}>
                      <FormControl sx={{ minWidth: 110 }} size="small">
                        <InputLabel sx={{ fontSize: '12px' }}>Method</InputLabel>
                        <Select label="Method" value={restMethod} onChange={(e) => setRestMethod(e.target.value)} disabled={isReadOnly} sx={{ fontSize: '12px' }}>
                          <MenuItem value="GET" sx={{ fontSize: '12px' }}>GET</MenuItem>
                          <MenuItem value="POST" sx={{ fontSize: '12px' }}>POST</MenuItem>
                          <MenuItem value="PUT" sx={{ fontSize: '12px' }}>PUT</MenuItem>
                          <MenuItem value="DELETE" sx={{ fontSize: '12px' }}>DELETE</MenuItem>
                          <MenuItem value="PATCH" sx={{ fontSize: '12px' }}>PATCH</MenuItem>
                        </Select>
                      </FormControl>
                      <TextField
                        fullWidth
                        size="small"
                        type="number"
                        label="Timeout (sec)"
                        value={restTimeout}
                        onChange={(e) => setRestTimeout(e.target.value)}
                        disabled={isReadOnly}
                        placeholder="10"
                      />
                    </Box>

                    {/* Endpoint URL */}
                    <TextField
                      fullWidth
                      size="small"
                      label="Endpoint URL"
                      value={restUrl}
                      onChange={(e) => setRestUrl(e.target.value)}
                      disabled={isReadOnly}
                      placeholder="https://api.external.com/v1/verify or ${context.apiUrl}"
                      helperText={!isReadOnly ? "Target URL. Supports ${context.field} placeholders" : undefined}
                      slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                      sx={{ mb: 1.5 }}
                    />

                    {/* Request Headers */}
                    <TextField
                      fullWidth
                      size="small"
                      label="Headers (JSON)"
                      value={restHeadersStr}
                      onChange={(e) => setRestHeadersStr(e.target.value)}
                      disabled={isReadOnly}
                      multiline
                      rows={2}
                      placeholder={`{\n  "Authorization": "Bearer ...",\n  "Content-Type": "application/json"\n}`}
                      slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                      sx={{ mb: 2 }}
                    />

                    {/* Outgoing Payload / Input Mapping Card */}
                    <Paper elevation={0} sx={{ p: 1.5, mb: 2, bgcolor: 'background.paper', border: '1px solid rgba(56,189,248,0.25)', borderRadius: 2 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                          <Typography variant="caption" sx={{ fontWeight: 800, color: '#38bdf8', letterSpacing: 0.5, textTransform: 'uppercase' }}>
                            📤 Outgoing Payload Mapping
                          </Typography>
                          <Chip label="Context ➔ API" size="small" sx={{ height: 18, fontSize: '10px', bgcolor: 'rgba(56,189,248,0.15)', color: '#38bdf8', fontWeight: 700 }} />
                        </Box>
                        <Chip
                          label={isValidJson(inputMappingStr) ? "Valid JSON" : "Invalid JSON"}
                          size="small"
                          color={isValidJson(inputMappingStr) ? "success" : "error"}
                          variant="outlined"
                          sx={{ height: 16, fontSize: '9px' }}
                        />
                      </Box>
                      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1, fontSize: '11px', lineHeight: 1.4 }}>
                        Maps variables from workflow context into the outbound request body. Both formats are supported: <code>&#123;&quot;apiField&quot;: &quot;context.var&quot;&#125;</code> or <code>&#123;&quot;context.var&quot;: &quot;apiField&quot;&#125;</code>.
                      </Typography>

                      {/* Available Context Schema Variables chips */}
                      {schemaFields && schemaFields.length > 0 && !isReadOnly && (
                        <Box sx={{ mb: 1, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5 }}>
                          <Typography variant="caption" sx={{ fontSize: '10px', color: 'text.secondary', fontWeight: 700 }}>
                            + Quick Add Context Var:
                          </Typography>
                          {schemaFields.map(f => (
                            <Chip
                              key={f.fieldKey}
                              label={f.fieldKey}
                              size="small"
                              onClick={() => {
                                let current = {};
                                try { current = JSON.parse(inputMappingStr) || {}; } catch(e) {}
                                current[f.fieldKey] = `context.${f.fieldKey}`;
                                setInputMappingStr(JSON.stringify(current, null, 2));
                              }}
                              sx={{
                                height: 20,
                                fontSize: '10px',
                                bgcolor: 'action.hover',
                                cursor: 'pointer',
                                '&:hover': { bgcolor: 'rgba(56,189,248,0.2)', color: '#38bdf8' }
                              }}
                            />
                          ))}
                        </Box>
                      )}

                      <TextField
                        fullWidth
                        size="small"
                        value={inputMappingStr}
                        onChange={(e) => setInputMappingStr(e.target.value)}
                        disabled={isReadOnly}
                        multiline
                        rows={3}
                        placeholder={`{\n  "pan": "context.panNumber",\n  "circle": "context.circleId",\n  "refId": "context.businessKey"\n}`}
                        slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px', bgcolor: 'background.default' } } }}
                      />

                      {!isReadOnly && (
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 0.5 }}>
                          <Button
                            size="small"
                            variant="text"
                            onClick={() => {
                              const sample = {};
                              if (schemaFields.length > 0) {
                                schemaFields.slice(0, 3).forEach(f => {
                                  sample[f.fieldKey] = `context.${f.fieldKey}`;
                                });
                              } else {
                                sample["businessKey"] = "context.businessKey";
                                sample["trackingId"] = "context.trackingId";
                              }
                              setInputMappingStr(JSON.stringify(sample, null, 2));
                            }}
                            sx={{ fontSize: '10px', textTransform: 'none', color: '#38bdf8', p: 0 }}
                          >
                            Use Sample Outgoing Mapping
                          </Button>
                        </Box>
                      )}
                    </Paper>

                    {/* Incoming Response / Output Mapping Card */}
                    <Paper elevation={0} sx={{ p: 1.5, bgcolor: 'background.paper', border: '1px solid rgba(16,185,129,0.25)', borderRadius: 2 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                          <Typography variant="caption" sx={{ fontWeight: 800, color: '#10b981', letterSpacing: 0.5, textTransform: 'uppercase' }}>
                            📥 Incoming Response Mapping
                          </Typography>
                          <Chip label="API ➔ Context" size="small" sx={{ height: 18, fontSize: '10px', bgcolor: 'rgba(16,185,129,0.15)', color: '#10b981', fontWeight: 700 }} />
                        </Box>
                        <Chip
                          label={isValidJson(outputMappingStr) ? "Valid JSON" : "Invalid JSON"}
                          size="small"
                          color={isValidJson(outputMappingStr) ? "success" : "error"}
                          variant="outlined"
                          sx={{ height: 16, fontSize: '9px' }}
                        />
                      </Box>
                      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1, fontSize: '11px', lineHeight: 1.4 }}>
                        Extracts fields from API response and writes them to workflow context. Format: <code>&#123;&quot;responseField&quot;: &quot;context.targetField&quot;&#125;</code>. Dotted paths like <code>data.score</code> are supported.
                      </Typography>

                      {/* Available Context Schema Variables chips */}
                      {schemaFields && schemaFields.length > 0 && !isReadOnly && (
                        <Box sx={{ mb: 1, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5 }}>
                          <Typography variant="caption" sx={{ fontSize: '10px', color: 'text.secondary', fontWeight: 700 }}>
                            + Target Context Var:
                          </Typography>
                          {schemaFields.map(f => (
                            <Chip
                              key={f.fieldKey}
                              label={f.fieldKey}
                              size="small"
                              onClick={() => {
                                let current = {};
                                try { current = JSON.parse(outputMappingStr) || {}; } catch(e) {}
                                current[f.fieldKey] = `context.${f.fieldKey}`;
                                setOutputMappingStr(JSON.stringify(current, null, 2));
                              }}
                              sx={{
                                height: 20,
                                fontSize: '10px',
                                bgcolor: 'action.hover',
                                cursor: 'pointer',
                                '&:hover': { bgcolor: 'rgba(16,185,129,0.2)', color: '#10b981' }
                              }}
                            />
                          ))}
                        </Box>
                      )}

                      <TextField
                        fullWidth
                        size="small"
                        value={outputMappingStr}
                        onChange={(e) => setOutputMappingStr(e.target.value)}
                        disabled={isReadOnly}
                        multiline
                        rows={3}
                        placeholder={`{\n  "status": "context.kycStatus",\n  "score": "context.creditScore",\n  "data.verified": "context.isVerified"\n}`}
                        slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px', bgcolor: 'background.default' } } }}
                      />

                      {!isReadOnly && (
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 0.5 }}>
                          <Button
                            size="small"
                            variant="text"
                            onClick={() => {
                              const sample = {
                                "status": "context.kycStatus",
                                "score": "context.creditScore"
                              };
                              setOutputMappingStr(JSON.stringify(sample, null, 2));
                            }}
                            sx={{ fontSize: '10px', textTransform: 'none', color: '#10b981', p: 0 }}
                          >
                            Use Sample Response Mapping
                          </Button>
                        </Box>
                      )}
                    </Paper>
                  </Box>
                </>)}

                {/* 2. CREATE_BUCKET */}
                {commandType === 'CREATE_BUCKET' && (<>
                  {registeredBuckets.length > 0 && !isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                    <InputLabel sx={{ fontSize: '12px' }}>Target Bucket</InputLabel>
                    <Select label="Target Bucket" value={bucketId} onChange={(e) => setBucketId(e.target.value)} sx={{ fontSize: '12px' }}>
                      {registeredBuckets.map(b => (<MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                        {b.name} ({b.bucketId})
                      </MenuItem>))}
                    </Select>
                  </FormControl>) : (<TextField fullWidth size="small" label="Bucket ID" value={bucketId} onChange={(e) => setBucketId(e.target.value)} disabled={isReadOnly} placeholder="BCK_001" sx={{ mb: 1.5 }} />)}
                  {!isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                    <InputLabel sx={{ fontSize: '12px' }}>Dependency Buckets</InputLabel>
                    <Select multiple label="Dependency Buckets" value={dependencyBuckets} onChange={(e) => {
                      const val = e.target.value;
                      setDependencyBuckets(typeof val === 'string' ? val.split(',') : (val));
                    }} renderValue={(selected) => (<Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                      {(selected).map((value) => (<Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />))}
                    </Box>)} sx={{ fontSize: '12px' }}>
                      {registeredBuckets
                        .filter(b => b.bucketId !== bucketId)
                        .map(b => (<MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                          {b.name} ({b.bucketId})
                        </MenuItem>))}
                    </Select>
                  </FormControl>) : (dependencyBuckets.length > 0 && (<Box sx={{ mb: 1.5 }}>
                    <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>
                      DEPENDENCY BUCKETS
                    </Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                      {dependencyBuckets.map((value) => (<Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />))}
                    </Box>
                  </Box>))}
                </>)}

                {/* 3. UPDATE_FORM_STATUS */}
                {commandType === 'UPDATE_FORM_STATUS' && (<TextField fullWidth size="small" label="Form Status Value" value={formStatus} onChange={(e) => setFormStatus(e.target.value)} disabled={isReadOnly} placeholder="APPROVED" sx={{ mb: 1.5 }} />)}

                {/* 4. START_CHILD_WORKFLOW */}
                {(commandType === 'START_CHILD_WORKFLOW' || commandType === 'START_WORKFLOW') && (<>
                  {registeredWorkflows.length > 0 && !isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                    <InputLabel sx={{ fontSize: '12px' }}>Child Workflow Key</InputLabel>
                    <Select label="Child Workflow Key" value={childWorkflowKey} onChange={(e) => setChildWorkflowKey(e.target.value)} sx={{ fontSize: '12px' }}>
                      {registeredWorkflows.map(w => (<MenuItem key={w.id} value={w.key} sx={{ fontSize: '12px' }}>
                        {w.name} ({w.key})
                      </MenuItem>))}
                    </Select>
                  </FormControl>) : (<TextField fullWidth size="small" label="Child Workflow Key" value={childWorkflowKey} onChange={(e) => setChildWorkflowKey(e.target.value)} disabled={isReadOnly} placeholder="CHILD_FLOW_KEY" sx={{ mb: 1.5 }} />)}
                  <TextField fullWidth size="small" label="Input Mapping (JSON)" value={inputMappingStr} onChange={(e) => setInputMappingStr(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder={`{\n  "parentVar": "childVar"\n}`} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }} sx={{ mb: 1.5 }} />
                  <TextField fullWidth size="small" label="Output Mapping (JSON)" value={outputMappingStr} onChange={(e) => setOutputMappingStr(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder={`{\n  "childVar": "parentVar"\n}`} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }} />
                </>)}

                {/* 5. EMIT_EVENT */}
                {(commandType === 'EMIT_EVENT' || commandType === 'PUBLISH_EVENT') && (<>
                  {registeredEvents.length > 0 && !isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                    <InputLabel sx={{ fontSize: '12px' }}>Event Key</InputLabel>
                    <Select label="Event Key" value={eventType} onChange={(e) => setEventType(e.target.value)} sx={{ fontSize: '12px' }}>
                      {registeredEvents.map(ev => (<MenuItem key={ev.id} value={ev.eventKey} sx={{ fontSize: '12px' }}>
                        {ev.name} ({ev.eventKey})
                      </MenuItem>))}
                    </Select>
                  </FormControl>) : (<TextField fullWidth size="small" label="Event Key" value={eventType} onChange={(e) => setEventType(e.target.value)} disabled={isReadOnly} placeholder="PAYMENT_RECEIVED" sx={{ mb: 1.5 }} />)}
                  <TextField fullWidth size="small" label="Payload Mapping (JSON)" value={inputMappingStr} onChange={(e) => setInputMappingStr(e.target.value)} disabled={isReadOnly} multiline rows={4} placeholder={`{\n  "context['amount']": "amount",\n  "context.status": "status"\n}`} helperText={!isReadOnly ? "JSON mapping parent context SpEL expressions to outbound event payload keys" : undefined} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }} sx={{ mb: 1.5 }} />
                </>)}

                {/* 6. Dynamic Parameter Renderer for any other Backend Commands (e.g. FINALIZE_CAF_SUBMISSION, MQ, Custom) */}
                {(() => {
                  const meta = availableCommands.find(c => c.type === commandType);
                  if (!meta || !meta.parameters || meta.parameters.length === 0) return null;
                  if (['REST', 'CALL_EXTERNAL_SYSTEM', 'HTTP', 'CREATE_BUCKET', 'UPDATE_FORM_STATUS', 'START_CHILD_WORKFLOW', 'START_WORKFLOW', 'EMIT_EVENT', 'PUBLISH_EVENT'].includes(commandType)) {
                    return null;
                  }

                  return (
                    <Box sx={{ border: '1px solid rgba(255, 255, 255, 0.1)', borderRadius: 1.5, p: 1.5, mb: 1.5, bgcolor: 'rgba(255, 255, 255, 0.02)' }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: '#38bdf8', display: 'block', mb: 1, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                        {meta.displayName || meta.type} Parameters
                      </Typography>
                      {meta.parameters.map(param => {
                        const val = commandParams[param.name] !== undefined ? commandParams[param.name] : (param.defaultValue !== null && param.defaultValue !== undefined ? param.defaultValue : '');
                        const handleChange = (newVal) => {
                          setCommandParams(prev => ({ ...prev, [param.name]: newVal }));
                        };

                        if (param.type === 'select') {
                          let options = param.options || [];
                          if (param.dataSource === 'INTEGRATIONS') {
                            options = registeredIntegrations.map(i => ({ label: `${i.name} (${i.integrationKey})`, value: i.integrationKey }));
                          } else if (param.dataSource === 'BUCKETS') {
                            options = registeredBuckets.map(b => ({ label: `${b.name} (${b.bucketId})`, value: b.bucketId }));
                          } else if (param.dataSource === 'WORKFLOWS') {
                            options = registeredWorkflows.map(w => ({ label: `${w.name} (${w.key})`, value: w.key }));
                          } else if (param.dataSource === 'EVENTS') {
                            options = registeredEvents.map(ev => ({ label: `${ev.name} (${ev.eventKey})`, value: ev.eventKey }));
                          }

                          return (
                            <FormControl key={param.name} fullWidth size="small" sx={{ mb: 1.5 }}>
                              <InputLabel sx={{ fontSize: '12px' }}>{param.label}{param.required ? ' *' : ''}</InputLabel>
                              <Select
                                label={`${param.label}${param.required ? ' *' : ''}`}
                                value={val}
                                onChange={(e) => handleChange(e.target.value)}
                                disabled={isReadOnly}
                                sx={{ fontSize: '12px' }}
                              >
                                {options.map(opt => (
                                  <MenuItem key={opt.value} value={opt.value} sx={{ fontSize: '12px' }}>
                                    {opt.label}
                                  </MenuItem>
                                ))}
                              </Select>
                            </FormControl>
                          );
                        }

                        if (param.type === 'number') {
                          return (
                            <TextField
                              key={param.name}
                              fullWidth
                              size="small"
                              type="number"
                              label={param.label + (param.required ? ' *' : '')}
                              value={val}
                              onChange={(e) => handleChange(e.target.value === '' ? '' : Number(e.target.value))}
                              disabled={isReadOnly}
                              placeholder={param.placeholder}
                              helperText={param.description}
                              sx={{ mb: 1.5 }}
                            />
                          );
                        }

                        if (param.type === 'json') {
                          return (
                            <TextField
                              key={param.name}
                              fullWidth
                              size="small"
                              multiline
                              rows={3}
                              label={param.label + (param.required ? ' *' : '')}
                              value={typeof val === 'object' ? JSON.stringify(val, null, 2) : val}
                              onChange={(e) => handleChange(e.target.value)}
                              disabled={isReadOnly}
                              placeholder={param.placeholder || '{}'}
                              helperText={param.description}
                              slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                              sx={{ mb: 1.5 }}
                            />
                          );
                        }

                        return (
                          <TextField
                            key={param.name}
                            fullWidth
                            size="small"
                            label={param.label + (param.required ? ' *' : '')}
                            value={val}
                            onChange={(e) => handleChange(e.target.value)}
                            disabled={isReadOnly}
                            placeholder={param.placeholder}
                            helperText={param.description}
                            sx={{ mb: 1.5 }}
                          />
                        );
                      })}
                    </Box>
                  );
                })()}
              </>)}

              {/* WAIT_EVENT fields */}
              {nodeType === 'WAIT_EVENT' && (<>
                <TextField fullWidth size="small" label="Event Type" value={eventType} onChange={(e) => setEventType(e.target.value)} disabled={isReadOnly} placeholder="KAFKA_PAYMENT" helperText={!isReadOnly ? "Correlation event name to wait for (e.g. PAYMENT_COMPLETED)" : undefined} sx={{ mb: 1.5 }} />

                <Paper elevation={0} sx={{ p: 1.5, mb: 1.5, bgcolor: 'background.paper', border: '1px solid rgba(245,158,11,0.25)', borderRadius: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: '#f59e0b', letterSpacing: 0.5, textTransform: 'uppercase' }}>
                        📥 Event Payload ➔ Context Mapping
                      </Typography>
                    </Box>
                    <Chip
                      label={isValidJson(payloadMappingStr) ? "Valid JSON" : "Invalid JSON"}
                      size="small"
                      color={isValidJson(payloadMappingStr) ? "success" : "error"}
                      variant="outlined"
                      sx={{ height: 16, fontSize: '9px' }}
                    />
                  </Box>
                  <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1, fontSize: '11px' }}>
                    Maps incoming Kafka event payload fields into workflow context variables (e.g. <code>&#123;&quot;eventField&quot;: &quot;contextVar&quot;&#125;</code>).
                  </Typography>

                  {schemaFields && schemaFields.length > 0 && !isReadOnly && (
                    <Box sx={{ mb: 1, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5 }}>
                      <Typography variant="caption" sx={{ fontSize: '10px', color: 'text.secondary', fontWeight: 700 }}>
                        + Target Context Var:
                      </Typography>
                      {schemaFields.map(f => (
                        <Chip
                          key={f.fieldKey}
                          label={f.fieldKey}
                          size="small"
                          onClick={() => {
                            let current = {};
                            try { current = JSON.parse(payloadMappingStr) || {}; } catch(e) {}
                            current[f.fieldKey] = f.fieldKey;
                            setPayloadMappingStr(JSON.stringify(current, null, 2));
                          }}
                          sx={{
                            height: 20,
                            fontSize: '10px',
                            bgcolor: 'action.hover',
                            cursor: 'pointer',
                            '&:hover': { bgcolor: 'rgba(245,158,11,0.2)', color: '#f59e0b' }
                          }}
                        />
                      ))}
                    </Box>
                  )}

                  <TextField fullWidth size="small" value={payloadMappingStr} onChange={(e) => setPayloadMappingStr(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder={`{\n  "transactionId": "paymentTxId",\n  "amount": "paymentAmount"\n}`} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px', bgcolor: 'background.default' } } }} />
                </Paper>

                <TextField fullWidth size="small" label="Payload Routes (JSON Array)" value={routesStr} onChange={(e) => setRoutesStr(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder={`[\n  { "value": "APPROVED", "target": "NODE_APPROVED" },\n  { "value": "REJECTED", "target": "NODE_REJECTED" }\n]`} helperText={!isReadOnly ? "Map payload outcomes to target node IDs" : undefined} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }} sx={{ mb: 1.5 }} />

                <TextField fullWidth size="small" label="Default Route (Target Node ID)" value={defaultRoute} onChange={(e) => setDefaultRoute(e.target.value)} disabled={isReadOnly} placeholder="NODE_DEFAULT" helperText={!isReadOnly ? "Fallback node ID if no route matches" : undefined} />
              </>)}

              {/* SUB_WORKFLOW fields */}
              {nodeType === 'SUB_WORKFLOW' && (<>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 0.5 }}>
                    SUB-WORKFLOW SETTINGS
                  </Typography>
                </Box>
                {registeredWorkflows.length > 0 && !isReadOnly ? (<FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                  <InputLabel sx={{ fontSize: '12px' }}>Child Workflow Key</InputLabel>
                  <Select label="Child Workflow Key" value={childWorkflowKey} onChange={(e) => setChildWorkflowKey(e.target.value)} sx={{ fontSize: '12px' }}>
                    {registeredWorkflows.map(w => (<MenuItem key={w.id} value={w.key} sx={{ fontSize: '12px' }}>
                      {w.name} ({w.key})
                    </MenuItem>))}
                  </Select>
                </FormControl>) : (<TextField fullWidth size="small" label="Child Workflow Key" value={childWorkflowKey} onChange={(e) => setChildWorkflowKey(e.target.value)} disabled={isReadOnly} placeholder="CHILD_FLOW_KEY" sx={{ mb: 1.5 }} />)}

                <TextField fullWidth size="small" label="📤 Input Mapping (Parent Context ➔ Child Context)" value={inputMappingStr} onChange={(e) => setInputMappingStr(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder={`{\n  "parentVar": "childVar"\n}`} helperText={!isReadOnly ? "JSON mapping parent context variables to child workflow inputs" : undefined} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }} sx={{ mb: 1.5 }} />

                <TextField fullWidth size="small" label="📥 Output Mapping (Child Context ➔ Parent Context)" value={outputMappingStr} onChange={(e) => setOutputMappingStr(e.target.value)} disabled={isReadOnly} multiline rows={3} placeholder={`{\n  "childVar": "parentVar"\n}`} helperText={!isReadOnly ? "JSON mapping child workflow outputs back into parent context" : undefined} slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }} />
              </>)}
            </Box>
          </Box>

          {/* Footer */}
          {!isReadOnly && (<Box sx={{ p: 2, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', flexDirection: 'column', gap: 1 }}>
            {saved && <Alert severity="success" sx={{ mb: 1, borderRadius: 1, py: 0.5 }}>Saved!</Alert>}
            <Button fullWidth variant="contained" startIcon={isSaving ? <CircularProgress size={14} color="inherit" /> : <SaveIcon />} onClick={handleSave} disabled={isSaving} sx={{
              background: `linear-gradient(135deg, ${accentColor} 0%, ${accentColor}cc 100%)`,
              boxShadow: `0 4px 12px ${accentColor}40`,
              fontWeight: 700,
            }}>
              {isSaving ? 'Saving...' : 'Save Node'}
            </Button>
            {onDeleteNode && (<Button fullWidth variant="outlined" color="error" startIcon={<DeleteIcon />} onClick={() => onDeleteNode(node.id)} sx={{
              borderColor: 'rgba(244,67,54,0.3)',
              color: '#f44336',
              '&:hover': {
                borderColor: '#f44336',
                bgcolor: 'rgba(244,67,54,0.04)',
              }
            }}>
              Delete Node
            </Button>)}
          </Box>)}
        </Box>)}
    </Drawer>);
};
