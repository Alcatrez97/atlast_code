import React, { useState, useEffect } from 'react';
import {
  Box, Typography, Button, TextField, MenuItem, Select, FormControl, InputLabel,
  CircularProgress, Chip, Card, CardContent, Alert, IconButton, Switch, FormControlLabel
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import HistoryIcon from '@mui/icons-material/History';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import { useWorkflowStore } from '../store/workflowStore';
import type { ExecutionLog, StepRecord } from '../store/workflowStore';
import { ValidationResultBanner } from './ValidationResultBanner';

const STEP_STATUS_COLORS: Record<string, string> = {
  ENTERED: '#6366f1',
  EVALUATED: '#f59e0b',
  ROUTED: '#14b8a6',
  COMPLETED: '#10b981',
  FAILED: '#ef4444',
  SKIPPED: '#6b7280',
};

const NODE_TYPE_COLORS: Record<string, string> = {
  START: '#10b981',
  END: '#ef4444',
  RULE: '#6366f1',
  DECISION: '#f59e0b',
  BUCKET: '#a855f7',
  TIMER: '#14b8a6',
  PARALLEL: '#f97316',
  JOIN: '#f97316',
};

interface ExecutionPanelProps {
  onShowNotification: (msg: string, severity: 'success' | 'error') => void;
}

export const ExecutionPanel: React.FC<ExecutionPanelProps> = ({ onShowNotification }) => {
  const { workflows, setView, setCurrentExecution, setReplayVersion, goBack } = useWorkflowStore();

  const [selectedKey, setSelectedKey] = useState('');
  const [contextJson, setContextJson] = useState(JSON.stringify({
    amount: 15000,
    status: 'APPROVED',
    risk: 45,
    customerId: 'CUST-001'
  }, null, 2));
  const [jsonError, setJsonError] = useState<string | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);
  const [result, setResult] = useState<ExecutionLog | null>(null);
  const [activeStep, setActiveStep] = useState<number | null>(null);

  // Schema and form states
  const [schema, setSchema] = useState<any | null>(null);
  const [formValues, setFormValues] = useState<Record<string, any>>({});
  const [advancedMode, setAdvancedMode] = useState(true);

  const parseFieldValue = (val: string, type: string) => {
    if (type === 'NUMBER') {
      const num = Number(val);
      return isNaN(num) ? '' : num;
    }
    if (type === 'BOOLEAN') {
      return val.toLowerCase() === 'true';
    }
    return val;
  };

  useEffect(() => {
    if (!selectedKey) {
      setSchema(null);
      setAdvancedMode(true);
      return;
    }

    const loadSchema = async () => {
      try {
        const res = await fetch(`/api/context-schemas/${selectedKey}`);
        if (res.ok) {
          const data = await res.json();
          setSchema(data);
          setAdvancedMode(false); // Enable form by default

          // Initialize form values
          const initialVals: Record<string, any> = {};
          data.fields.forEach((f: any) => {
            if (f.defaultValue !== undefined && f.defaultValue !== null && f.defaultValue !== '') {
              initialVals[f.fieldKey] = parseFieldValue(f.defaultValue, f.fieldType);
            } else {
              if (f.fieldType === 'BOOLEAN') initialVals[f.fieldKey] = false;
              else if (f.fieldType === 'NUMBER') initialVals[f.fieldKey] = '';
              else initialVals[f.fieldKey] = '';
            }
          });
          setFormValues(initialVals);
        } else {
          setSchema(null);
          setAdvancedMode(true);
        }
      } catch {
        setSchema(null);
        setAdvancedMode(true);
      }
    };

    loadSchema();
  }, [selectedKey]);

  const publishedWorkflows = workflows.filter(w => w.activeVersion != null);

  const validateJson = (value: string) => {
    try {
      JSON.parse(value);
      setJsonError(null);
      return true;
    } catch {
      setJsonError('Invalid JSON. Please fix syntax errors.');
      return false;
    }
  };

  const handleExecute = async () => {
    if (!selectedKey) {
      onShowNotification('Please select a workflow to execute.', 'error');
      return;
    }

    let context: Record<string, any> = {};
    if (advancedMode) {
      if (!validateJson(contextJson)) return;
      context = JSON.parse(contextJson);
    } else {
      const errors: string[] = [];
      schema.fields.forEach((f: any) => {
        const val = formValues[f.fieldKey];
        if (f.required && (val === undefined || val === null || val === '')) {
          errors.push(`"${f.displayName}" is required.`);
        }

        if (val !== undefined && val !== null && val !== '') {
          if (f.fieldType === 'NUMBER') {
            const num = Number(val);
            if (isNaN(num)) {
              errors.push(`"${f.displayName}" must be a number.`);
            } else {
              context[f.fieldKey] = num;
            }
          } else if (f.fieldType === 'BOOLEAN') {
            context[f.fieldKey] = val === true || val === 'true';
          } else {
            context[f.fieldKey] = val;
          }
        }
      });

      if (errors.length > 0) {
        onShowNotification(errors[0], 'error');
        return;
      }
    }

    setIsExecuting(true);
    setResult(null);
    setActiveStep(null);

    try {
      const response = await fetch(`/api/execute/${selectedKey}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ context }),
      });

      const data: ExecutionLog = await response.json();
      setResult(data);
      setCurrentExecution(data);
      if (data.status === 'COMPLETED') {
        onShowNotification(`Execution completed! Reached: ${data.outcomeNodeLabel || 'END'}`, 'success');
      } else {
        onShowNotification(`Execution failed: ${data.errorMessage}`, 'error');
      }
    } catch (err: any) {
      onShowNotification(err.message || 'Execution request failed', 'error');
    } finally {
      setIsExecuting(false);
    }
  };

  const handleViewReplay = async () => {
    if (!result) return;
    // Load the version for replay
    try {
      const response = await fetch(`/api/workflows/versions/${result.versionId}`);
      const version = await response.json();
      setReplayVersion(version);
      setCurrentExecution(result);
      setView('replay');
    } catch {
      onShowNotification('Could not load workflow version for replay.', 'error');
    }
  };

  const stepColor = (step: StepRecord) => STEP_STATUS_COLORS[step.status] || '#6b7280';
  const nodeColor = (type: string) => NODE_TYPE_COLORS[type?.toUpperCase()] || '#6366f1';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', bgcolor: 'background.default', color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      {/* Header */}
      <Box sx={{
        display: 'flex', alignItems: 'center', px: 3, py: 2,
        bgcolor: 'background.paper', borderBottom: '1px solid', borderColor: 'divider'
      }}>
        <IconButton onClick={() => goBack()} sx={{ mr: 2, color: 'text.secondary' }}>
          <ArrowBackIcon />
        </IconButton>
        <PlayArrowIcon sx={{ color: '#10b981', mr: 1.5 }} />
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>Workflow Executor</Typography>
          <Typography variant="caption" color="text.secondary">Execute a published workflow with a context payload</Typography>
        </Box>
        <Button
          variant="outlined"
          size="small"
          startIcon={<HistoryIcon />}
          onClick={() => goBack()}
          sx={{ borderColor: 'rgba(255,255,255,0.1)' }}
        >
          Back
        </Button>
      </Box>

      {/* Body */}
      <Box sx={{ display: 'flex', flexGrow: 1, overflow: 'hidden' }}>

        {/* Left: Input Panel */}
        <Box sx={{
          width: 420, borderRight: '1px solid rgba(255,255,255,0.08)',
          display: 'flex', flexDirection: 'column', p: 3, gap: 2.5, overflowY: 'auto'
        }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 800, color: 'text.primary' }}>
            Execution Configuration
          </Typography>

          {publishedWorkflows.length === 0 ? (
            <Alert severity="info" sx={{ borderRadius: 2 }}>
              No workflows with a published version found. Publish a version to enable execution.
            </Alert>
          ) : (
            <FormControl fullWidth size="small">
              <InputLabel>Select Workflow</InputLabel>
              <Select
                value={selectedKey}
                label="Select Workflow"
                onChange={(e) => setSelectedKey(e.target.value)}
                sx={{ bgcolor: 'rgba(255,255,255,0.03)' }}
              >
                {publishedWorkflows.map(w => (
                  <MenuItem key={w.id} value={w.key}>
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>{w.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{w.key} · v{w.activeVersion}</Typography>
                    </Box>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                CONTEXT PAYLOAD
              </Typography>
              {schema && (
                <FormControlLabel
                  control={
                    <Switch
                      size="small"
                      checked={advancedMode}
                      onChange={(e) => setAdvancedMode(e.target.checked)}
                    />
                  }
                  label={<Typography sx={{ fontSize: '10px', fontWeight: 600, color: 'text.secondary' }}>JSON</Typography>}
                  sx={{ m: 0 }}
                />
              )}
            </Box>

            {advancedMode ? (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5, lineHeight: 1.5 }}>
                  This object is available in rule expressions as <code style={{ color: '#6366f1' }}>context.fieldName</code> or{' '}
                  <code style={{ color: '#6366f1' }}>context['fieldName']</code>.
                </Typography>
                <TextField
                  fullWidth
                  multiline
                  rows={14}
                  value={contextJson}
                  onChange={(e) => {
                    setContextJson(e.target.value);
                    validateJson(e.target.value);
                  }}
                  error={!!jsonError}
                  helperText={jsonError}
                  slotProps={{
                    input: {
                      sx: {
                        fontFamily: 'monospace',
                        fontSize: '12px',
                        bgcolor: 'rgba(0,0,0,0.3)',
                        color: '#e2e8f0',
                        '& textarea': { resize: 'vertical' }
                      }
                    }
                  }}
                />
              </Box>
            ) : (
              <Box sx={{
                display: 'flex', flexDirection: 'column', gap: 2, p: 2,
                border: '1px solid rgba(255,255,255,0.06)', borderRadius: 2, bgcolor: 'rgba(0,0,0,0.15)'
              }}>
                {schema.fields.map((f: any) => (
                  <Box key={f.fieldKey}>
                    {f.fieldType === 'BOOLEAN' ? (
                      <FormControlLabel
                        control={
                          <Switch
                            checked={formValues[f.fieldKey] === true}
                            onChange={(e) => setFormValues({ ...formValues, [f.fieldKey]: e.target.checked })}
                            color="primary"
                          />
                        }
                        label={
                          <Typography variant="body2" sx={{ fontWeight: 700, fontSize: '12px' }}>
                            {f.displayName} {f.required && <span style={{ color: '#ef4444' }}>*</span>}
                          </Typography>
                        }
                      />
                    ) : (
                      <TextField
                        fullWidth size="small"
                        label={`${f.displayName}${f.required ? ' *' : ''}`}
                        type={f.fieldType === 'NUMBER' ? 'number' : 'text'}
                        value={formValues[f.fieldKey] ?? ''}
                        onChange={(e) => setFormValues({ ...formValues, [f.fieldKey]: e.target.value })}
                        placeholder={f.defaultValue || ''}
                        helperText={f.description}
                        slotProps={{
                          input: { sx: { color: '#fff', fontSize: '12px' } },
                          formHelperText: { sx: { fontSize: '9px', color: 'text.secondary' } }
                        }}
                        sx={{
                          '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' },
                          '& .MuiInputLabel-root': { fontSize: '12px' }
                        }}
                      />
                    )}
                  </Box>
                ))}
              </Box>
            )}
          </Box>

          <Button
            variant="contained"
            size="large"
            startIcon={isExecuting ? <CircularProgress size={18} color="inherit" /> : <PlayArrowIcon />}
            onClick={handleExecute}
            disabled={isExecuting || !selectedKey}
            sx={{
              background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
              boxShadow: '0 4px 14px rgba(16, 185, 129, 0.4)',
              fontWeight: 800,
              py: 1.5,
              '&:disabled': { opacity: 0.5 }
            }}
          >
            {isExecuting ? 'Executing...' : 'Execute Workflow'}
          </Button>

          {/* Quick reference */}
          <Card sx={{ bgcolor: 'rgba(99,102,241,0.07)', border: '1px solid rgba(99,102,241,0.15)' }}>
            <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Typography variant="caption" sx={{ fontWeight: 800, color: '#818cf8', display: 'block', mb: 1 }}>
                SpEL EXPRESSION EXAMPLES
              </Typography>
              {[
                "context['amount'] > 5000",
                "context.status == 'APPROVED'",
                "context['risk'] != null && context['risk'] > 80",
                "context.type?.toUpperCase() == 'LOAN'",
              ].map((ex, i) => (
                <Typography key={i} variant="caption" sx={{ display: 'block', fontFamily: 'monospace', color: '#c7d2fe', fontSize: '10px', mb: 0.5 }}>
                  {ex}
                </Typography>
              ))}
            </CardContent>
          </Card>
        </Box>

        {/* Right: Result Panel */}
        <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {!result ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 2, opacity: 0.4 }}>
              <PlayArrowIcon sx={{ fontSize: 64, color: '#10b981' }} />
              <Typography variant="h6" color="text.secondary">Execute a workflow to see the trace</Typography>
            </Box>
          ) : (
            <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
              {/* Result header */}
              <Box sx={{
                display: 'flex', alignItems: 'center', gap: 2, px: 3, py: 2,
                bgcolor: result.status === 'COMPLETED' ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
                borderBottom: '1px solid rgba(255,255,255,0.06)'
              }}>
                {result.status === 'COMPLETED'
                  ? <CheckCircleIcon sx={{ color: '#10b981', fontSize: 28 }} />
                  : <ErrorIcon sx={{ color: '#ef4444', fontSize: 28 }} />}
                <Box sx={{ flexGrow: 1 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 800, color: result.status === 'COMPLETED' ? '#10b981' : '#ef4444' }}>
                    Execution {result.status}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 2, mt: 0.5, flexWrap: 'wrap' }}>
                    <Typography variant="caption" color="text.secondary">
                      <AccessTimeIcon sx={{ fontSize: 10, mr: 0.5 }} />{result.totalDurationMs}ms
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Steps: {result.executionTrace?.length || 0}
                    </Typography>
                    {result.outcomeNodeLabel && (
                      <Typography variant="caption" color="text.secondary">
                        Outcome: <strong style={{ color: '#a855f7' }}>{result.outcomeNodeLabel}</strong>
                      </Typography>
                    )}
                  </Box>
                </Box>
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={handleViewReplay}
                    sx={{ borderColor: 'rgba(99,102,241,0.4)', color: '#818cf8' }}
                  >
                    🔍 View on Canvas
                  </Button>
                </Box>
              </Box>

              {/* Validation warnings or errors */}
              {result.inputContext && result.inputContext._validation && (
                <ValidationResultBanner validation={result.inputContext._validation} />
              )}

              {result.errorMessage && (
                <Alert severity="error" sx={{ m: 2, borderRadius: 2 }}>{result.errorMessage}</Alert>
              )}

              {/* Execution trace */}
              <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 2.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 1 }}>
                  EXECUTION TRACE ({result.executionTrace?.length || 0} STEPS)
                </Typography>
                {result.executionTrace?.map((step, i) => (
                  <Card
                    key={i}
                    onClick={() => setActiveStep(activeStep === i ? null : i)}
                    sx={{
                      bgcolor: activeStep === i ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.02)',
                      border: `1px solid ${activeStep === i ? stepColor(step) + '50' : 'rgba(255,255,255,0.05)'}`,
                      borderLeft: `3px solid ${stepColor(step)}`,
                      borderRadius: 2,
                      cursor: 'pointer',
                      transition: 'all 0.15s',
                      '&:hover': { bgcolor: 'rgba(255,255,255,0.04)' }
                    }}
                  >
                    <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <Box sx={{
                          width: 24, height: 24, borderRadius: '50%',
                          bgcolor: nodeColor(step.nodeType) + '20',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          border: `1px solid ${nodeColor(step.nodeType)}40`,
                          flexShrink: 0
                        }}>
                          <Typography sx={{ fontSize: '8px', fontWeight: 800, color: nodeColor(step.nodeType) }}>
                            {step.stepIndex + 1}
                          </Typography>
                        </Box>
                        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {step.label || step.nodeType}
                            </Typography>
                            <Chip
                              label={step.nodeType}
                              size="small"
                              sx={{ height: 16, fontSize: '8px', fontWeight: 800, bgcolor: nodeColor(step.nodeType) + '20', color: nodeColor(step.nodeType), border: 'none' }}
                            />
                            <Chip
                              label={step.status}
                              size="small"
                              sx={{ height: 16, fontSize: '8px', fontWeight: 800, bgcolor: stepColor(step) + '20', color: stepColor(step), border: 'none' }}
                            />
                          </Box>
                          {step.notes && (
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25, lineHeight: 1.3 }}>
                              {step.notes}
                            </Typography>
                          )}
                        </Box>
                        {step.durationMs !== undefined && (
                          <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
                            {step.durationMs}ms
                          </Typography>
                        )}
                      </Box>
                      {/* Expanded view */}
                      {activeStep === i && step.expression && (
                        <Box sx={{ mt: 1.5, pt: 1.5, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                          <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>EXPRESSION</Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#818cf8', display: 'block', mb: 1 }}>{step.expression}</Typography>
                          <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 0.5 }}>RESULT</Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#10b981' }}>
                            {JSON.stringify(step.expressionResult)}
                          </Typography>
                        </Box>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </Box>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
};
