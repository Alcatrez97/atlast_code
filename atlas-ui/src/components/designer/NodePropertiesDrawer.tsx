import React, { useState, useEffect } from 'react';
import {
  Drawer, Box, Typography, IconButton, Divider, TextField, Button,
  Chip, CircularProgress, Alert, FormControl, InputLabel, Select, MenuItem, Tooltip
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import SaveIcon from '@mui/icons-material/Save';
import InfoIcon from '@mui/icons-material/Info';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { useWorkflowStore } from '../../store/workflowStore';
import type { StepRecord } from '../../store/workflowStore';

interface NodeData {
  id: string;
  type: string;
  label?: string;
  data?: Record<string, any>;
}

interface NodePropertiesDrawerProps {
  open: boolean;
  node: NodeData | null;
  isReadOnly: boolean;
  traceStep?: StepRecord | null;
  onClose: () => void;
  onSaveNode?: (nodeId: string, updates: { label: string; data: Record<string, any> }) => Promise<void>;
  onDeleteNode?: (nodeId: string) => Promise<void> | void;
}

const NODE_TYPE_COLORS: Record<string, string> = {
  START: '#10b981', END: '#ef4444', RULE: '#6366f1',
  DECISION: '#f59e0b', BUCKET: '#a855f7', TIMER: '#14b8a6',
  PARALLEL: '#f97316', JOIN: '#f97316', SUB_WORKFLOW: '#3b82f6',
  COMMAND: '#38bdf8', WAIT_EVENT: '#f59e0b'
};

const STEP_STATUS_COLORS: Record<string, string> = {
  ENTERED: '#6366f1', EVALUATED: '#f59e0b', ROUTED: '#14b8a6',
  COMPLETED: '#10b981', FAILED: '#ef4444', SKIPPED: '#6b7280',
  WAITING: '#f59e0b'
};

export const NodePropertiesDrawer: React.FC<NodePropertiesDrawerProps> = ({
  open, node, isReadOnly, traceStep, onClose, onSaveNode, onDeleteNode
}) => {
  const [label, setLabel] = useState('');
  const [expression, setExpression] = useState('');
  const [bucketId, setBucketId] = useState('');
  const [dependencyBuckets, setDependencyBuckets] = useState<string[]>([]);
  const [ruleId, setRuleId] = useState('');
  const [delayMs, setDelayMs] = useState('');
  const [decisionField, setDecisionField] = useState('');
  const [decisionType, setDecisionType] = useState<'FIELD' | 'EXPRESSION'>('FIELD');
  const [childWorkflowKey, setChildWorkflowKey] = useState('');
  const [inputMappingStr, setInputMappingStr] = useState('{}');
  const [outputMappingStr, setOutputMappingStr] = useState('{}');
  const [commandType, setCommandType] = useState('');
  const [eventType, setEventType] = useState('');
  const [routesStr, setRoutesStr] = useState('[]');
  const [defaultRoute, setDefaultRoute] = useState('');
  const [formStatus, setFormStatus] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const { selectedWorkflow } = useWorkflowStore();
  const [schemaFields, setSchemaFields] = useState<any[]>([]);
  const [registeredBuckets, setRegisteredBuckets] = useState<any[]>([]);
  const [registeredRules, setRegisteredRules] = useState<any[]>([]);
  const [registeredWorkflows, setRegisteredWorkflows] = useState<any[]>([]);
  const [registeredEvents, setRegisteredEvents] = useState<any[]>([]);

  // Fetch schema fields, registered buckets, and registered rules when drawer opens
  useEffect(() => {
    if (!open || !selectedWorkflow) return;

    fetch(`/api/context-schemas/${selectedWorkflow.key}`)
      .then(res => res.ok ? res.json() : null)
      .then(data => {
        if (data && data.fields) {
          setSchemaFields(data.fields);
        } else {
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
      } else {
        setInputMappingStr(node.data?.inputMapping ? JSON.stringify(node.data.inputMapping, null, 2) : '{}');
      }
      setOutputMappingStr(node.data?.outputMapping ? JSON.stringify(node.data.outputMapping, null, 2) : '{}');
      setCommandType(node.data?.commandType || node.data?.type || '');
      setEventType(node.data?.eventType || node.data?.eventKey || '');
      setRoutesStr(node.data?.routes ? JSON.stringify(node.data.routes, null, 2) : '[]');
      setDefaultRoute(node.data?.defaultRoute || '');
      setFormStatus(node.data?.formStatus || node.data?.status || '');
      setSaved(false);
    }
  }, [node]);

  const nodeType = node?.type?.toUpperCase() || '';
  const accentColor = NODE_TYPE_COLORS[nodeType] || '#6366f1';

  const handleSave = async () => {
    if (!node || !onSaveNode) return;
    setIsSaving(true);
    try {
      const updatedData: Record<string, any> = { ...node.data };
      if (nodeType === 'RULE') {
        updatedData.expression = expression;
        updatedData.ruleId = ruleId;
      } else if (nodeType === 'DECISION') {
        if (decisionType === 'EXPRESSION') {
          updatedData.expression = expression;
          delete updatedData.decisionField;
        } else {
          updatedData.decisionField = decisionField;
          delete updatedData.expression;
        }
      } else if (nodeType === 'BUCKET') {
        updatedData.bucketId = bucketId;
        updatedData.dependencyBuckets = dependencyBuckets;
      } else if (nodeType === 'TIMER') {
        updatedData.delayMs = delayMs;
      } else if (nodeType === 'SUB_WORKFLOW') {
        updatedData.childWorkflowKey = childWorkflowKey;
        try {
          updatedData.inputMapping = JSON.parse(inputMappingStr || '{}');
        } catch (e) {
          alert('Invalid JSON in Input Mapping');
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
      } else if (nodeType === 'COMMAND') {
        updatedData.commandType = commandType;
        if (commandType === 'CREATE_BUCKET') {
          updatedData.bucketId = bucketId;
          updatedData.dependencyBuckets = dependencyBuckets;
        } else if (commandType === 'UPDATE_FORM_STATUS') {
          updatedData.formStatus = formStatus;
        } else if (commandType === 'START_CHILD_WORKFLOW' || commandType === 'START_WORKFLOW') {
          updatedData.childWorkflowKey = childWorkflowKey;
          try {
            updatedData.inputMapping = JSON.parse(inputMappingStr || '{}');
          } catch (e) {
            alert('Invalid JSON in Input Mapping');
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
        } else if (commandType === 'EMIT_EVENT' || commandType === 'PUBLISH_EVENT') {
          updatedData.eventType = eventType;
          updatedData.eventKey = eventType;
          try {
            updatedData.payloadMapping = JSON.parse(inputMappingStr || '{}');
          } catch (e) {
            alert('Invalid JSON in Payload Mapping');
            setIsSaving(false);
            return;
          }
        }
      } else if (nodeType === 'WAIT_EVENT') {
        updatedData.eventType = eventType;
        try {
          updatedData.routes = JSON.parse(routesStr || '[]');
        } catch (e) {
          alert('Invalid JSON in Routes configuration');
          setIsSaving(false);
          return;
        }
        updatedData.defaultRoute = defaultRoute;
      }
      await onSaveNode(node.id, { label, data: updatedData });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      variant="persistent"
      slotProps={{
        paper: {
          sx: {
            width: 340,
            bgcolor: 'background.paper',
            borderLeft: `1px solid ${accentColor}30`,
            boxShadow: `-8px 0 32px rgba(0,0,0,0.2)`,
            color: 'text.primary'
          }
        }
      }}
    >
      {node && (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header */}
          <Box sx={{
            display: 'flex', alignItems: 'center', p: 2,
            borderBottom: `1px solid ${accentColor}20`,
            background: `linear-gradient(135deg, ${accentColor}10 0%, transparent 100%)`
          }}>
            <Box sx={{
              width: 32, height: 32, borderRadius: 2,
              bgcolor: accentColor + '20',
              border: `1px solid ${accentColor}40`,
              display: 'flex', alignItems: 'center', justifyContent: 'center', mr: 1.5
            }}>
              {isReadOnly ? <InfoIcon sx={{ fontSize: 16, color: accentColor }} /> : <EditIcon sx={{ fontSize: 16, color: accentColor }} />}
            </Box>
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                {node.label || nodeType}
              </Typography>
              <Box sx={{ display: 'flex', gap: 0.5, mt: 0.25 }}>
                <Chip label={nodeType} size="small" sx={{ height: 16, fontSize: '8px', fontWeight: 800, bgcolor: accentColor + '20', color: accentColor, border: 'none' }} />
                {isReadOnly && <Chip label="READ ONLY" size="small" sx={{ height: 16, fontSize: '8px', bgcolor: 'rgba(255,255,255,0.05)', color: 'text.secondary', border: 'none' }} />}
              </Box>
            </Box>
            <IconButton size="small" onClick={onClose}><CloseIcon fontSize="small" /></IconButton>
          </Box>

          <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 2, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            {/* Trace step info (trace/read-only mode) */}
            {traceStep && (
              <Box>
                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 1, display: 'block', mb: 1 }}>
                  EXECUTION TRACE
                </Typography>
                <Box sx={{
                  p: 1.5, borderRadius: 2,
                  bgcolor: `${STEP_STATUS_COLORS[traceStep.status] || '#6366f1'}10`,
                  border: `1px solid ${STEP_STATUS_COLORS[traceStep.status] || '#6366f1'}30`
                }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <Chip
                      label={traceStep.status}
                      size="small"
                      sx={{ height: 18, fontSize: '9px', fontWeight: 800, bgcolor: `${STEP_STATUS_COLORS[traceStep.status]}20`, color: STEP_STATUS_COLORS[traceStep.status] }}
                    />
                    {traceStep.durationMs !== undefined && (
                      <Typography variant="caption" color="text.secondary">{traceStep.durationMs}ms</Typography>
                    )}
                  </Box>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.5 }}>
                    {traceStep.notes}
                  </Typography>
                  {traceStep.expression && (
                    <Box sx={{ mt: 1.5 }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>EXPRESSION</Typography>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#818cf8', display: 'block', wordBreak: 'break-all' }}>
                        {traceStep.expression}
                      </Typography>
                    </Box>
                  )}
                  {traceStep.expressionResult !== undefined && traceStep.expressionResult !== null && (
                    <Box sx={{ mt: 1 }}>
                      <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>RESULT</Typography>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#10b981' }}>
                        {JSON.stringify(traceStep.expressionResult)}
                      </Typography>
                    </Box>
                  )}
                </Box>
                <Divider sx={{ mt: 2 }} />
              </Box>
            )}

            {/* Node ID */}
            <Box>
              <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 1, display: 'block', mb: 1 }}>
                NODE PROPERTIES
              </Typography>
              <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#6b7280', display: 'block', mb: 1.5, wordBreak: 'break-all' }}>
                ID: {node.id}
              </Typography>

              {/* Label */}
              <TextField
                fullWidth size="small" label="Label"
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                disabled={isReadOnly}
                sx={{ mb: 1.5 }}
              />

              {/* RULE fields */}
              {nodeType === 'RULE' && (
                <>
                  {/* Registry Rule selector */}
                  {!isReadOnly && (
                    <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                      <InputLabel sx={{ fontSize: '12px' }}>Reference Registry Rule</InputLabel>
                      <Select
                        label="Reference Registry Rule"
                        value={ruleId}
                        onChange={(e) => {
                          const val = e.target.value;
                          setRuleId(val);
                          if (val) {
                            setExpression(''); // clear inline expression when rule reference is selected
                          }
                        }}
                        sx={{ fontSize: '12px' }}
                      >
                        <MenuItem value="" sx={{ fontSize: '12px' }}>
                          <em>None (Inline SpEL Expression)</em>
                        </MenuItem>
                        {registeredRules.map(r => (
                          <MenuItem key={r.id} value={r.ruleKey} sx={{ fontSize: '12px' }}>
                            {r.name} ({r.ruleKey}) {!r.active && '(INACTIVE)'}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  )}

                  {ruleId ? (
                    (() => {
                      const matched = registeredRules.find(r => r.ruleKey === ruleId);
                      if (matched) {
                        return (
                          <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.15)', mb: 1.5 }}>
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
                          </Box>
                        );
                      }
                      return (
                        <Alert severity="warning" sx={{ mb: 1.5, py: 0.5, borderRadius: 1.5, fontSize: '11px' }}>
                          Warning: Referenced rule key '<code>{ruleId}</code>' not found in registry!
                        </Alert>
                      );
                    })()
                  ) : (
                    <>
                      {schemaFields.length > 0 && !isReadOnly && (
                        <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                          <InputLabel sx={{ fontSize: '12px' }}>Insert Known Context Field</InputLabel>
                          <Select
                            label="Insert Known Context Field"
                            value=""
                            onChange={(e) => {
                              const val = e.target.value;
                              if (val) {
                                setExpression(prev => prev + `context['${val}']`);
                              }
                            }}
                            sx={{ fontSize: '12px' }}
                          >
                            {schemaFields.map(f => (
                              <MenuItem key={f.fieldKey} value={f.fieldKey} sx={{ fontSize: '12px' }}>
                                {f.displayName} ({f.fieldKey} · {f.fieldType})
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      )}
                      <TextField
                        fullWidth size="small" label="SpEL Expression"
                        value={expression}
                        onChange={(e) => setExpression(e.target.value)}
                        disabled={isReadOnly}
                        multiline rows={3}
                        placeholder="context['amount'] > 5000"
                        helperText={!isReadOnly ? "Use context.field or context['field'] to access payload values" : undefined}
                        slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '12px' } } }}
                        sx={{ mb: 1.5 }}
                      />
                    </>
                  )}
                </>
              )}

              {/* DECISION fields */}
              {nodeType === 'DECISION' && (
                <>
                  {!isReadOnly && (
                    <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                      <InputLabel sx={{ fontSize: '12px' }}>Decision Source</InputLabel>
                      <Select
                        label="Decision Source"
                        value={decisionType}
                        onChange={(e) => setDecisionType(e.target.value as 'FIELD' | 'EXPRESSION')}
                        sx={{ fontSize: '12px' }}
                      >
                        <MenuItem value="FIELD" sx={{ fontSize: '12px' }}>Context Field Key</MenuItem>
                        <MenuItem value="EXPRESSION" sx={{ fontSize: '12px' }}>Custom SpEL Expression</MenuItem>
                      </Select>
                    </FormControl>
                  )}

                  {isReadOnly && (
                    <Chip
                      label={decisionType === 'EXPRESSION' ? 'SpEL Expression Mode' : 'Context Field Mode'}
                      size="small"
                      sx={{ mb: 1.5, height: 20, fontSize: '9px', fontWeight: 800, bgcolor: 'rgba(255,255,255,0.05)', color: 'text.secondary', border: 'none' }}
                    />
                  )}

                  {decisionType === 'FIELD' ? (
                    schemaFields.length > 0 && !isReadOnly ? (
                      <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                        <InputLabel sx={{ fontSize: '12px' }}>Decision Field Key</InputLabel>
                        <Select
                          label="Decision Field Key"
                          value={decisionField}
                          onChange={(e) => setDecisionField(e.target.value)}
                          sx={{ fontSize: '12px' }}
                        >
                          {schemaFields.map(f => (
                            <MenuItem key={f.fieldKey} value={f.fieldKey} sx={{ fontSize: '12px' }}>
                              {f.displayName} ({f.fieldKey})
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    ) : (
                      <TextField
                        fullWidth size="small" label="Decision Field Key"
                        value={decisionField}
                        onChange={(e) => setDecisionField(e.target.value)}
                        disabled={isReadOnly}
                        placeholder="status"
                        helperText={!isReadOnly ? "Context field name to evaluate for routing (e.g. 'status')" : undefined}
                      />
                    )
                  ) : (
                    <>
                      {schemaFields.length > 0 && !isReadOnly && (
                        <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                          <InputLabel sx={{ fontSize: '12px' }}>Insert Known Context Field</InputLabel>
                          <Select
                            label="Insert Known Context Field"
                            value=""
                            onChange={(e) => {
                              const val = e.target.value;
                              if (val) {
                                setExpression(prev => prev + `context['${val}']`);
                              }
                            }}
                            sx={{ fontSize: '12px' }}
                          >
                            {schemaFields.map(f => (
                              <MenuItem key={f.fieldKey} value={f.fieldKey} sx={{ fontSize: '12px' }}>
                                {f.displayName} ({f.fieldKey} · {f.fieldType})
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      )}
                      <TextField
                        fullWidth size="small" label="SpEL Expression"
                        value={expression}
                        onChange={(e) => setExpression(e.target.value)}
                        disabled={isReadOnly}
                        multiline rows={3}
                        placeholder="context['amount'] > 10000 ? 'HIGH' : 'LOW'"
                        helperText={!isReadOnly ? "SpEL statement that evaluates to an outcome matching outgoing edge conditions" : undefined}
                        slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '12px' } } }}
                        sx={{ mb: 1.5 }}
                      />
                    </>
                  )}
                </>
              )}

              {/* BUCKET fields */}
              {nodeType === 'BUCKET' && (
                <>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                    <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 0.5 }}>
                      BUCKET SETTINGS
                    </Typography>
                    <Tooltip
                      title={
                        <Box sx={{ p: 1, maxWidth: 260 }}>
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
                        </Box>
                      }
                      arrow
                      placement="left"
                    >
                      <IconButton size="small" sx={{ color: '#a855f7', p: 0.5 }}>
                        <InfoIcon sx={{ fontSize: '16px' }} />
                      </IconButton>
                    </Tooltip>
                  </Box>

                  {registeredBuckets.length > 0 && !isReadOnly ? (
                    <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                      <InputLabel sx={{ fontSize: '12px' }}>Outcome Bucket</InputLabel>
                      <Select
                        label="Outcome Bucket"
                        value={bucketId}
                        onChange={(e) => setBucketId(e.target.value)}
                        sx={{ fontSize: '12px' }}
                      >
                        {registeredBuckets.map(b => (
                          <MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                            {b.name} ({b.bucketId})
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  ) : (
                    <TextField
                      fullWidth size="small" label="Bucket ID"
                      value={bucketId}
                      onChange={(e) => setBucketId(e.target.value)}
                      disabled={isReadOnly}
                      placeholder="BCK_001"
                      sx={{ mb: 1.5 }}
                    />
                  )}

                  {/* Dependency Buckets selection */}
                  {!isReadOnly ? (
                    <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                      <InputLabel sx={{ fontSize: '12px' }}>Dependency Buckets</InputLabel>
                      <Select
                        multiple
                        label="Dependency Buckets"
                        value={dependencyBuckets}
                        onChange={(e) => {
                          const val = e.target.value;
                          setDependencyBuckets(typeof val === 'string' ? val.split(',') : (val as string[]));
                        }}
                        renderValue={(selected) => (
                          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                            {(selected as string[]).map((value) => (
                              <Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />
                            ))}
                          </Box>
                        )}
                        sx={{ fontSize: '12px' }}
                      >
                        {registeredBuckets
                          .filter(b => b.bucketId !== bucketId)
                          .map(b => (
                            <MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                              {b.name} ({b.bucketId})
                            </MenuItem>
                          ))}
                      </Select>
                    </FormControl>
                  ) : (
                    dependencyBuckets.length > 0 && (
                      <Box sx={{ mb: 1.5 }}>
                        <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>
                          DEPENDENCY BUCKETS
                        </Typography>
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                          {dependencyBuckets.map((value) => (
                            <Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />
                          ))}
                        </Box>
                      </Box>
                    )
                  )}

                  {(() => {
                    const matched = registeredBuckets.find(b => b.bucketId === bucketId);
                    if (matched) {
                      return (
                        <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(168,85,247,0.06)', border: '1px solid rgba(168,85,247,0.15)', mt: 1 }}>
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
                        </Box>
                      );
                    }
                    return null;
                  })()}
                </>
              )}

              {/* TIMER fields */}
              {nodeType === 'TIMER' && (
                <TextField
                  fullWidth size="small" label="Delay (ms)"
                  value={delayMs}
                  onChange={(e) => setDelayMs(e.target.value)}
                  disabled={isReadOnly}
                  type="number"
                  placeholder="5000"
                />
              )}

              {nodeType === 'COMMAND' && (
                <>
                  <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                    <InputLabel sx={{ fontSize: '12px' }}>Command Type</InputLabel>
                    <Select
                      label="Command Type"
                      value={commandType}
                      onChange={(e) => setCommandType(e.target.value)}
                      disabled={isReadOnly}
                      sx={{ fontSize: '12px' }}
                    >
                      <MenuItem value="CREATE_BUCKET" sx={{ fontSize: '12px' }}>Create Bucket (CREATE_BUCKET)</MenuItem>
                      <MenuItem value="UPDATE_FORM_STATUS" sx={{ fontSize: '12px' }}>Update Form Status (UPDATE_FORM_STATUS)</MenuItem>
                      <MenuItem value="START_CHILD_WORKFLOW" sx={{ fontSize: '12px' }}>Start Child Workflow (START_CHILD_WORKFLOW)</MenuItem>
                      <MenuItem value="EMIT_EVENT" sx={{ fontSize: '12px' }}>Emit Event (EMIT_EVENT)</MenuItem>
                      <MenuItem value="SEND_NOTIFICATION" sx={{ fontSize: '12px' }}>Send Notification (SEND_NOTIFICATION)</MenuItem>
                      <MenuItem value="CALL_EXTERNAL_SYSTEM" sx={{ fontSize: '12px' }}>Call External System (CALL_EXTERNAL_SYSTEM)</MenuItem>
                      <MenuItem value="CREATE_CASE" sx={{ fontSize: '12px' }}>Create Case (CREATE_CASE)</MenuItem>
                    </Select>
                  </FormControl>

                  {commandType === 'CREATE_BUCKET' && (
                    <>
                      {registeredBuckets.length > 0 && !isReadOnly ? (
                        <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                          <InputLabel sx={{ fontSize: '12px' }}>Target Bucket</InputLabel>
                          <Select
                            label="Target Bucket"
                            value={bucketId}
                            onChange={(e) => setBucketId(e.target.value)}
                            sx={{ fontSize: '12px' }}
                          >
                            {registeredBuckets.map(b => (
                              <MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                                {b.name} ({b.bucketId})
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      ) : (
                        <TextField
                          fullWidth size="small" label="Bucket ID"
                          value={bucketId}
                          onChange={(e) => setBucketId(e.target.value)}
                          disabled={isReadOnly}
                          placeholder="BCK_001"
                          sx={{ mb: 1.5 }}
                        />
                      )}
                      {!isReadOnly ? (
                        <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                          <InputLabel sx={{ fontSize: '12px' }}>Dependency Buckets</InputLabel>
                          <Select
                            multiple
                            label="Dependency Buckets"
                            value={dependencyBuckets}
                            onChange={(e) => {
                              const val = e.target.value;
                              setDependencyBuckets(typeof val === 'string' ? val.split(',') : (val as string[]));
                            }}
                            renderValue={(selected) => (
                              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                                {(selected as string[]).map((value) => (
                                  <Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />
                                ))}
                              </Box>
                            )}
                            sx={{ fontSize: '12px' }}
                          >
                            {registeredBuckets
                              .filter(b => b.bucketId !== bucketId)
                              .map(b => (
                                <MenuItem key={b.bucketId} value={b.bucketId} sx={{ fontSize: '12px' }}>
                                  {b.name} ({b.bucketId})
                                </MenuItem>
                              ))}
                          </Select>
                        </FormControl>
                      ) : (
                        dependencyBuckets.length > 0 && (
                          <Box sx={{ mb: 1.5 }}>
                            <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>
                              DEPENDENCY BUCKETS
                            </Typography>
                            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                              {dependencyBuckets.map((value) => (
                                <Chip key={value} label={value} size="small" sx={{ height: 18, fontSize: '10px' }} />
                              ))}
                            </Box>
                          </Box>
                        )
                      )}
                    </>
                  )}

                  {commandType === 'UPDATE_FORM_STATUS' && (
                    <TextField
                      fullWidth size="small" label="Form Status Value"
                      value={formStatus}
                      onChange={(e) => setFormStatus(e.target.value)}
                      disabled={isReadOnly}
                      placeholder="APPROVED"
                      sx={{ mb: 1.5 }}
                    />
                  )}

                  {(commandType === 'START_CHILD_WORKFLOW' || commandType === 'START_WORKFLOW') && (
                    <>
                      {registeredWorkflows.length > 0 && !isReadOnly ? (
                        <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                          <InputLabel sx={{ fontSize: '12px' }}>Child Workflow Key</InputLabel>
                          <Select
                            label="Child Workflow Key"
                            value={childWorkflowKey}
                            onChange={(e) => setChildWorkflowKey(e.target.value)}
                            sx={{ fontSize: '12px' }}
                          >
                            {registeredWorkflows.map(w => (
                              <MenuItem key={w.id} value={w.key} sx={{ fontSize: '12px' }}>
                                {w.name} ({w.key})
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      ) : (
                        <TextField
                          fullWidth size="small" label="Child Workflow Key"
                          value={childWorkflowKey}
                          onChange={(e) => setChildWorkflowKey(e.target.value)}
                          disabled={isReadOnly}
                          placeholder="CHILD_FLOW_KEY"
                          sx={{ mb: 1.5 }}
                        />
                      )}
                      <TextField
                        fullWidth size="small" label="Input Mapping (JSON)"
                        value={inputMappingStr}
                        onChange={(e) => setInputMappingStr(e.target.value)}
                        disabled={isReadOnly}
                        multiline rows={3}
                        placeholder={`{\n  "parentVar": "childVar"\n}`}
                        slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                        sx={{ mb: 1.5 }}
                      />
                      <TextField
                        fullWidth size="small" label="Output Mapping (JSON)"
                        value={outputMappingStr}
                        onChange={(e) => setOutputMappingStr(e.target.value)}
                        disabled={isReadOnly}
                        multiline rows={3}
                        placeholder={`{\n  "childVar": "parentVar"\n}`}
                        slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                      />
                    </>
                  )}

                  {(commandType === 'EMIT_EVENT' || commandType === 'PUBLISH_EVENT') && (
                    <>
                      {registeredEvents.length > 0 && !isReadOnly ? (
                        <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                          <InputLabel sx={{ fontSize: '12px' }}>Event Key</InputLabel>
                          <Select
                            label="Event Key"
                            value={eventType}
                            onChange={(e) => setEventType(e.target.value)}
                            sx={{ fontSize: '12px' }}
                          >
                            {registeredEvents.map(ev => (
                              <MenuItem key={ev.id} value={ev.eventKey} sx={{ fontSize: '12px' }}>
                                {ev.name} ({ev.eventKey})
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      ) : (
                        <TextField
                          fullWidth size="small" label="Event Key"
                          value={eventType}
                          onChange={(e) => setEventType(e.target.value)}
                          disabled={isReadOnly}
                          placeholder="PAYMENT_RECEIVED"
                          sx={{ mb: 1.5 }}
                        />
                      )}
                      <TextField
                        fullWidth size="small" label="Payload Mapping (JSON)"
                        value={inputMappingStr}
                        onChange={(e) => setInputMappingStr(e.target.value)}
                        disabled={isReadOnly}
                        multiline rows={4}
                        placeholder={`{\n  "context['amount']": "amount",\n  "context.status": "status"\n}`}
                        helperText={!isReadOnly ? "JSON mapping parent context SpEL expressions to outbound event payload keys" : undefined}
                        slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                        sx={{ mb: 1.5 }}
                      />
                    </>
                  )}
                </>
              )}

              {/* WAIT_EVENT fields */}
              {nodeType === 'WAIT_EVENT' && (
                <>
                  <TextField
                    fullWidth size="small" label="Event Type"
                    value={eventType}
                    onChange={(e) => setEventType(e.target.value)}
                    disabled={isReadOnly}
                    placeholder="BUCKET_COMPLETED"
                    helperText={!isReadOnly ? "Correlation event name to wait for (e.g. BUCKET_COMPLETED)" : undefined}
                    sx={{ mb: 1.5 }}
                  />

                  <TextField
                    fullWidth size="small" label="Payload Routes (JSON Array)"
                    value={routesStr}
                    onChange={(e) => setRoutesStr(e.target.value)}
                    disabled={isReadOnly}
                    multiline rows={4}
                    placeholder={`[\n  { "value": "APPROVED", "target": "NODE_APPROVED" },\n  { "value": "REJECTED", "target": "NODE_REJECTED" }\n]`}
                    helperText={!isReadOnly ? "Map payload outcomes to target node IDs" : undefined}
                    slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                    sx={{ mb: 1.5 }}
                  />

                  <TextField
                    fullWidth size="small" label="Default Route (Target Node ID)"
                    value={defaultRoute}
                    onChange={(e) => setDefaultRoute(e.target.value)}
                    disabled={isReadOnly}
                    placeholder="NODE_DEFAULT"
                    helperText={!isReadOnly ? "Fallback node ID if no route matches" : undefined}
                  />
                </>
              )}

              {/* SUB_WORKFLOW fields */}
              {nodeType === 'SUB_WORKFLOW' && (
                <>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                    <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 0.5 }}>
                      SUB-WORKFLOW SETTINGS
                    </Typography>
                  </Box>

                  {registeredWorkflows.length > 0 && !isReadOnly ? (
                    <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
                      <InputLabel sx={{ fontSize: '12px' }}>Child Workflow Key</InputLabel>
                      <Select
                        label="Child Workflow Key"
                        value={childWorkflowKey}
                        onChange={(e) => setChildWorkflowKey(e.target.value)}
                        sx={{ fontSize: '12px' }}
                      >
                        {registeredWorkflows.map(w => (
                          <MenuItem key={w.id} value={w.key} sx={{ fontSize: '12px' }}>
                            {w.name} ({w.key})
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  ) : (
                    <TextField
                      fullWidth size="small" label="Child Workflow Key"
                      value={childWorkflowKey}
                      onChange={(e) => setChildWorkflowKey(e.target.value)}
                      disabled={isReadOnly}
                      placeholder="CHILD_FLOW_KEY"
                      sx={{ mb: 1.5 }}
                    />
                  )}

                  <TextField
                    fullWidth size="small" label="Input Mapping (JSON)"
                    value={inputMappingStr}
                    onChange={(e) => setInputMappingStr(e.target.value)}
                    disabled={isReadOnly}
                    multiline rows={4}
                    placeholder={`{\n  "parentVar": "childVar"\n}`}
                    helperText={!isReadOnly ? "JSON mapping parent context vars to child inputs" : undefined}
                    slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                    sx={{ mb: 1.5 }}
                  />

                  <TextField
                    fullWidth size="small" label="Output Mapping (JSON)"
                    value={outputMappingStr}
                    onChange={(e) => setOutputMappingStr(e.target.value)}
                    disabled={isReadOnly}
                    multiline rows={4}
                    placeholder={`{\n  "childVar": "parentVar"\n}`}
                    helperText={!isReadOnly ? "JSON mapping child outputs to parent context vars" : undefined}
                    slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '11px' } } }}
                  />
                </>
              )}
            </Box>
          </Box>

          {/* Footer */}
          {!isReadOnly && (
            <Box sx={{ p: 2, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', flexDirection: 'column', gap: 1 }}>
              {saved && <Alert severity="success" sx={{ mb: 1, borderRadius: 1, py: 0.5 }}>Saved!</Alert>}
              <Button
                fullWidth
                variant="contained"
                startIcon={isSaving ? <CircularProgress size={14} color="inherit" /> : <SaveIcon />}
                onClick={handleSave}
                disabled={isSaving}
                sx={{
                  background: `linear-gradient(135deg, ${accentColor} 0%, ${accentColor}cc 100%)`,
                  boxShadow: `0 4px 12px ${accentColor}40`,
                  fontWeight: 700,
                }}
              >
                {isSaving ? 'Saving...' : 'Save Node'}
              </Button>
              {onDeleteNode && (
                <Button
                  fullWidth
                  variant="outlined"
                  color="error"
                  startIcon={<DeleteIcon />}
                  onClick={() => onDeleteNode(node.id)}
                  sx={{
                    borderColor: 'rgba(244,67,54,0.3)',
                    color: '#f44336',
                    '&:hover': {
                      borderColor: '#f44336',
                      bgcolor: 'rgba(244,67,54,0.04)',
                    }
                  }}
                >
                  Delete Node
                </Button>
              )}
            </Box>
          )}
        </Box>
      )}
    </Drawer>
  );
};
