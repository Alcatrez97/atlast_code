import React, { useEffect, useState } from 'react';
import {
  Box, Container, Grid, Paper, Typography, Button, TextField, Select,
  MenuItem, FormControl, InputLabel, Switch, IconButton,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Card, CardContent, CircularProgress, Divider, Dialog, DialogTitle,
  DialogContent, DialogActions, Tooltip, FormControlLabel, Chip
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import SaveIcon from '@mui/icons-material/Save';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SettingsIcon from '@mui/icons-material/Settings';
import CodeIcon from '@mui/icons-material/Code';
import SettingsInputComponentIcon from '@mui/icons-material/SettingsInputComponent';
import InputIcon from '@mui/icons-material/Input';
import { useWorkflowStore } from '../store/workflowStore';
import type { ContextSchema, ContextField, Integration } from '../store/workflowStore';

interface ContextSchemaPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const ContextSchemaPage: React.FC<ContextSchemaPageProps> = ({ onShowNotification }) => {
  const { workflows, goBack } = useWorkflowStore();
  const [schemas, setSchemas] = useState<ContextSchema[]>([]);
  const [selectedKey, setSelectedKey] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [integrations, setIntegrations] = useState<Integration[]>([]);

  // Form states
  const [schemaName, setSchemaName] = useState('');
  const [schemaDesc, setSchemaDesc] = useState('');
  const [fields, setFields] = useState<ContextField[]>([]);
  const [schemaId, setSchemaId] = useState<string | null>(null);

  // Resolution Dialog States
  const [resolutionDialogOpen, setResolutionDialogOpen] = useState(false);
  const [selectedFieldIndex, setSelectedFieldIndex] = useState<number | null>(null);

  // Dialog form variables (temp state)
  const [resolutionType, setResolutionType] = useState<'INPUT' | 'INTEGRATION' | 'DERIVED'>('INPUT');
  const [integrationId, setIntegrationId] = useState('');
  const [responseMapping, setResponseMapping] = useState('');
  const [cacheable, setCacheable] = useState(true);
  const [ttlSeconds, setTtlSeconds] = useState(300);
  const [cost, setCost] = useState('LOW');
  const [expression, setExpression] = useState('');

  // Fetch all schemas
  const fetchSchemas = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/context-schemas');
      if (!res.ok) throw new Error('Failed to fetch context schemas');
      const data = await res.json();
      setSchemas(data);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  // Fetch integrations list
  const fetchIntegrations = async () => {
    try {
      const res = await fetch('/api/integrations');
      if (res.ok) {
        const data = await res.json();
        setIntegrations(data);
      }
    } catch (err) {
      console.error('Failed to fetch integrations', err);
    }
  };

  useEffect(() => {
    fetchSchemas();
    fetchIntegrations();
    if (workflows.length > 0) {
      setSelectedKey(workflows[0].key);
    }
  }, [workflows]);

  // Load selected schema
  useEffect(() => {
    if (!selectedKey) return;
    const existing = schemas.find(s => s.workflowKey === selectedKey);
    if (existing) {
      setSchemaId(existing.id);
      setSchemaName(existing.name);
      setSchemaDesc(existing.description || '');
      setFields(existing.fields || []);
    } else {
      setSchemaId(null);
      const wf = workflows.find(w => w.key === selectedKey);
      setSchemaName(wf ? `${wf.name} Schema` : '');
      setSchemaDesc('');
      setFields([]);
    }
  }, [selectedKey, schemas, workflows]);

  const handleAddField = () => {
    const newField: ContextField = {
      fieldKey: '',
      displayName: '',
      fieldType: 'STRING',
      required: false,
      defaultValue: '',
      description: '',
      cacheable: true,
      ttlSeconds: 300,
      cost: 'LOW'
    };
    setFields([...fields, newField]);
  };

  const handleRemoveField = (index: number) => {
    setFields(fields.filter((_, i) => i !== index));
  };

  const handleFieldChange = (index: number, key: keyof ContextField, value: any) => {
    setFields(fields.map((f, i) => i === index ? { ...f, [key]: value } : f));
  };

  const moveField = (index: number, direction: 'up' | 'down') => {
    const nextIndex = direction === 'up' ? index - 1 : index + 1;
    if (nextIndex < 0 || nextIndex >= fields.length) return;
    const updated = [...fields];
    const temp = updated[index];
    updated[index] = updated[nextIndex];
    updated[nextIndex] = temp;
    setFields(updated);
  };

  // Open Resolution Settings Dialog
  const handleOpenResolutionDialog = (index: number) => {
    setSelectedFieldIndex(index);
    const field = fields[index];

    // Determine initial type
    if (field.expression && field.expression.trim() !== '') {
      setResolutionType('DERIVED');
    } else if (field.integrationId && field.integrationId.trim() !== '') {
      setResolutionType('INTEGRATION');
    } else {
      setResolutionType('INPUT');
    }

    setIntegrationId(field.integrationId || '');
    setResponseMapping(field.responseMapping || '');
    setCacheable(field.cacheable !== undefined ? field.cacheable : true);
    setTtlSeconds(field.ttlSeconds || 300);
    setCost(field.cost || 'LOW');
    setExpression(field.expression || '');

    setResolutionDialogOpen(true);
  };

  // Save Resolution Settings from Dialog
  const handleSaveResolution = () => {
    if (selectedFieldIndex === null) return;

    const updatedField = { ...fields[selectedFieldIndex] };

    if (resolutionType === 'INPUT') {
      updatedField.integrationId = undefined;
      updatedField.responseMapping = undefined;
      updatedField.expression = undefined;
    } else if (resolutionType === 'INTEGRATION') {
      updatedField.integrationId = integrationId;
      updatedField.responseMapping = responseMapping;
      updatedField.expression = undefined;
    } else if (resolutionType === 'DERIVED') {
      updatedField.integrationId = undefined;
      updatedField.responseMapping = undefined;
      updatedField.expression = expression;
    }

    updatedField.cacheable = cacheable;
    updatedField.ttlSeconds = ttlSeconds;
    updatedField.cost = cost;

    setFields(fields.map((f, i) => i === selectedFieldIndex ? updatedField : f));
    setResolutionDialogOpen(false);
    onShowNotification('Resolution provider settings updated for this field.', 'success');
  };

  const handleSave = async () => {
    if (!selectedKey) return;
    if (!schemaName.trim()) {
      onShowNotification('Schema name is required', 'error');
      return;
    }

    // Validate fields
    for (let i = 0; i < fields.length; i++) {
      const f = fields[i];
      if (!f.fieldKey.trim()) {
        onShowNotification(`Field ${i + 1} has no Field Key`, 'error');
        return;
      }
      if (!f.displayName.trim()) {
        onShowNotification(`Field ${i + 1} (${f.fieldKey}) has no Display Name`, 'error');
        return;
      }
    }

    setLoading(true);
    try {
      const payload = {
        id: schemaId || undefined,
        workflowKey: selectedKey,
        name: schemaName,
        description: schemaDesc,
        fields: fields.map((f, idx) => ({ ...f, fieldOrder: idx }))
      };

      const res = await fetch('/api/context-schemas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to save schema');
      }

      onShowNotification('Context schema saved successfully!', 'success');
      await fetchSchemas();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!schemaId) return;
    if (!window.confirm('Are you sure you want to delete this context schema?')) return;

    setLoading(true);
    try {
      const res = await fetch(`/api/context-schemas/${schemaId}`, {
        method: 'DELETE'
      });
      if (!res.ok) throw new Error('Failed to delete schema');

      onShowNotification('Context schema deleted', 'success');
      setSchemaId(null);
      setFields([]);
      setSchemaDesc('');
      await fetchSchemas();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Title bar */}
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 4, gap: 2 }}>
          <IconButton onClick={() => goBack()} sx={{ color: '#14b8a6', border: '1px solid rgba(20,184,166,0.2)' }}>
            <ArrowBackIcon />
          </IconButton>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
              Context Schemas
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Define typed variables and required validation constraints for incoming execution payloads.
            </Typography>
          </Box>
        </Box>

        <Grid container spacing={3}>
          {/* Left panel: Selector */}
          <Grid size={{ xs: 12, md: 3 }}>
            <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
              <CardContent>
                <FormControl fullWidth size="small" variant="outlined" sx={{ mb: 2 }}>
                  <InputLabel id="workflow-select-label" sx={{ color: 'text.secondary' }}>Select Workflow</InputLabel>
                  <Select
                    labelId="workflow-select-label"
                    value={selectedKey}
                    label="Select Workflow"
                    onChange={(e) => setSelectedKey(e.target.value)}
                    sx={{ color: 'text.primary', '.MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
                  >
                    {workflows.map((wf) => (
                      <MenuItem key={wf.key} value={wf.key}>
                        {wf.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <Divider sx={{ my: 2, borderColor: 'rgba(255,255,255,0.06)' }} />

                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 1.5 }}>
                  SCHEMA REGISTRY STATUS
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  {workflows.map(wf => {
                    const hasSchema = schemas.some(s => s.workflowKey === wf.key);
                    return (
                      <Box key={wf.key} sx={{
                        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        p: 1, borderRadius: 1, bgcolor: selectedKey === wf.key ? 'rgba(20,184,166,0.08)' : 'transparent',
                        border: selectedKey === wf.key ? '1px solid rgba(20,184,166,0.3)' : '1px solid transparent'
                      }}>
                        <Typography variant="caption" sx={{ fontWeight: 600, color: selectedKey === wf.key ? '#14b8a6' : 'text.primary' }}>
                          {wf.name}
                        </Typography>
                        <span style={{
                          fontSize: '9px', fontWeight: 800, padding: '2px 6px', borderRadius: '10px',
                          background: hasSchema ? 'rgba(16,185,129,0.15)' : 'rgba(244,67,54,0.15)',
                          color: hasSchema ? '#10b981' : '#f44336'
                        }}>
                          {hasSchema ? 'DEFINED' : 'NO SCHEMA'}
                        </span>
                      </Box>
                    );
                  })}
                </Box>
              </CardContent>
            </Card>
          </Grid>

          {/* Right panel: Editor */}
          <Grid size={{ xs: 12, md: 9 }}>
            <Paper sx={{ p: 3, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              {loading && schemas.length === 0 ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
                  <CircularProgress color="primary" />
                </Box>
              ) : (
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 700, mb: 1, color: '#14b8a6' }}>
                    {schemaId ? 'Edit Schema Configuration' : 'Create Context Schema'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    Provide names, keys, data types, and default fallbacks. System will auto-validate these inputs before executing workflow key: <b>{selectedKey}</b>
                  </Typography>

                  <Grid container spacing={2} sx={{ mb: 4 }}>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        fullWidth size="small" label="Schema Name"
                        value={schemaName}
                        onChange={(e) => setSchemaName(e.target.value)}
                        slotProps={{ input: { sx: { color: 'text.primary' } } }}
                        sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        fullWidth size="small" label="Description"
                        value={schemaDesc}
                        onChange={(e) => setSchemaDesc(e.target.value)}
                        slotProps={{ input: { sx: { color: 'text.secondary' } } }}
                        sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
                      />
                    </Grid>
                  </Grid>

                  <Divider sx={{ my: 3, borderColor: 'rgba(255,255,255,0.06)' }} />

                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                      Field Contract Definitions ({fields.length})
                    </Typography>
                    <Button
                      variant="outlined" color="primary" size="small" startIcon={<AddIcon />}
                      onClick={handleAddField}
                      sx={{ color: '#14b8a6', borderColor: 'rgba(20,184,166,0.3)', '&:hover': { borderColor: '#14b8a6', bgcolor: 'rgba(20,184,166,0.05)' } }}
                    >
                      Add Field definition
                    </Button>
                  </Box>

                  {fields.length === 0 ? (
                    <Box sx={{ p: 4, border: '1px dashed rgba(255,255,255,0.08)', borderRadius: 2, textAlign: 'center', bgcolor: 'rgba(0,0,0,0.1)' }}>
                      <Typography variant="body2" color="text.secondary">
                        No fields defined. Execution context will bypass schema checks.
                      </Typography>
                    </Box>
                  ) : (
                    <TableContainer component={Paper} sx={{ bgcolor: 'transparent', border: '1px solid rgba(255,255,255,0.04)', boxShadow: 'none' }}>
                      <Table size="small">
                        <TableHead sx={{ bgcolor: 'rgba(0,0,0,0.2)' }}>
                          <TableRow>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800, width: 50 }}>Order</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800 }}>Field Key</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800 }}>Display Name</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800 }}>Type</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800, textAlign: 'center' }}>Required</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800 }}>Default Value</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800 }}>Resolution Provider</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 800, width: 80, textAlign: 'center' }}>Actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {fields.map((field, idx) => {
                            const isDerived = field.expression && field.expression.trim() !== '';
                            const isInt = field.integrationId && field.integrationId.trim() !== '';
                            const intKey = isInt ? integrations.find(i => i.id === field.integrationId)?.integrationKey || 'Integration' : '';

                            return (
                              <TableRow key={idx} sx={{ '&:hover': { bgcolor: 'rgba(255,255,255,0.01)' } }}>
                                {/* Ordering */}
                                <TableCell>
                                  <Box sx={{ display: 'flex', flexDirection: 'column' }}>
                                    <IconButton size="small" onClick={() => moveField(idx, 'up')} disabled={idx === 0} sx={{ p: 0.1, color: 'text.secondary' }}>
                                      <ArrowUpwardIcon sx={{ fontSize: 12 }} />
                                    </IconButton>
                                    <IconButton size="small" onClick={() => moveField(idx, 'down')} disabled={idx === fields.length - 1} sx={{ p: 0.1, color: 'text.secondary' }}>
                                      <ArrowDownwardIcon sx={{ fontSize: 12 }} />
                                    </IconButton>
                                  </Box>
                                </TableCell>
                                {/* Field Key */}
                                <TableCell>
                                  <TextField
                                    variant="standard" size="small" placeholder="amount"
                                    value={field.fieldKey}
                                    onChange={(e) => handleFieldChange(idx, 'fieldKey', e.target.value.replace(/\s+/g, ''))}
                                    slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '12px', color: 'text.primary' } } }}
                                  />
                                </TableCell>
                                {/* Display Name */}
                                <TableCell>
                                  <TextField
                                    variant="standard" size="small" placeholder="Amount"
                                    value={field.displayName}
                                    onChange={(e) => handleFieldChange(idx, 'displayName', e.target.value)}
                                    slotProps={{ input: { sx: { fontSize: '12px', color: 'text.primary' } } }}
                                  />
                                </TableCell>
                                {/* Type */}
                                <TableCell>
                                  <Select
                                    variant="standard" size="small"
                                    value={field.fieldType}
                                    onChange={(e) => handleFieldChange(idx, 'fieldType', e.target.value)}
                                    sx={{ color: 'text.primary', fontSize: '12px' }}
                                  >
                                    <MenuItem value="STRING">STRING</MenuItem>
                                    <MenuItem value="NUMBER">NUMBER</MenuItem>
                                    <MenuItem value="BOOLEAN">BOOLEAN</MenuItem>
                                    <MenuItem value="DATE">DATE</MenuItem>
                                  </Select>
                                </TableCell>
                                {/* Required */}
                                <TableCell sx={{ textAlign: 'center' }}>
                                  <Switch
                                    size="small" color="primary"
                                    checked={field.required}
                                    onChange={(e) => handleFieldChange(idx, 'required', e.target.checked)}
                                  />
                                </TableCell>
                                {/* Default Value */}
                                <TableCell>
                                  <TextField
                                    variant="standard" size="small" placeholder="0.0"
                                    value={field.defaultValue || ''}
                                    onChange={(e) => handleFieldChange(idx, 'defaultValue', e.target.value)}
                                    disabled={field.required}
                                    slotProps={{ input: { sx: { fontSize: '12px', color: 'text.primary' } } }}
                                  />
                                </TableCell>
                                {/* Resolution Settings Trigger */}
                                <TableCell>
                                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                    <Chip
                                      size="small"
                                      icon={isDerived ? <CodeIcon style={{ fontSize: 13 }} /> : isInt ? <SettingsInputComponentIcon style={{ fontSize: 12 }} /> : <InputIcon style={{ fontSize: 12 }} />}
                                      label={isDerived ? "Derived" : isInt ? intKey : "Input/Static"}
                                      color={isDerived ? "secondary" : isInt ? "primary" : "default"}
                                      variant="outlined"
                                      sx={{ fontSize: '10px', height: 20, bgcolor: 'rgba(0,0,0,0.2)' }}
                                    />
                                    <Tooltip title="Configure lazy resolution details">
                                      <IconButton size="small" onClick={() => handleOpenResolutionDialog(idx)} sx={{ color: '#14b8a6' }}>
                                        <SettingsIcon sx={{ fontSize: 16 }} />
                                      </IconButton>
                                    </Tooltip>
                                  </Box>
                                </TableCell>
                                {/* Actions */}
                                <TableCell sx={{ textAlign: 'center' }}>
                                  <IconButton size="small" color="error" onClick={() => handleRemoveField(idx)}>
                                    <DeleteIcon sx={{ fontSize: 16 }} />
                                  </IconButton>
                                </TableCell>
                              </TableRow>
                            );
                          })}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}

                  <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mt: 4 }}>
                    {schemaId && (
                      <Button
                        variant="outlined" color="error" startIcon={<DeleteIcon />}
                        onClick={handleDelete} disabled={loading}
                      >
                        Delete Schema
                      </Button>
                    )}
                    <Button
                      variant="contained" startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />}
                      onClick={handleSave} disabled={loading}
                      sx={{
                        background: 'linear-gradient(135deg, #14b8a6 0%, #0d9488 100%)',
                        boxShadow: '0 4px 14px rgba(20,184,166,0.3)',
                        fontWeight: 700,
                        '&:hover': {
                          background: '#0d9488'
                        }
                      }}
                    >
                      {loading ? 'Saving...' : 'Save Schema Contract'}
                    </Button>
                  </Box>
                </Box>
              )}
            </Paper>
          </Grid>
        </Grid>
      </Container>

      {/* Advanced Resolution Dialog */}
      <Dialog
        open={resolutionDialogOpen}
        onClose={() => setResolutionDialogOpen(false)}
        slotProps={{
          paper: {
            sx: { bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', color: 'text.primary', minWidth: 460 }
          }
        }}
      >
        <DialogTitle sx={{ fontWeight: 800, borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
          Lazy Resolution: {selectedFieldIndex !== null ? fields[selectedFieldIndex].fieldKey : ''}
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pt: 3 }}>
          {/* Resolution Source Type */}
          <FormControl fullWidth size="small">
            <InputLabel id="res-type-label" sx={{ color: 'text.secondary' }}>Resolution Mode</InputLabel>
            <Select
              labelId="res-type-label"
              value={resolutionType}
              label="Resolution Mode"
              onChange={(e) => setResolutionType(e.target.value as any)}
              sx={{ color: '#fff', '.MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
            >
              <MenuItem value="INPUT">Input Payload (No API/Calculation)</MenuItem>
              <MenuItem value="INTEGRATION">Fetch from Integration Provider</MenuItem>
              <MenuItem value="DERIVED">Derived Variable (Calculate via SpEL)</MenuItem>
            </Select>
          </FormControl>

          {/* Integration Provider configs */}
          {resolutionType === 'INTEGRATION' && (
            <>
              <FormControl fullWidth size="small">
                <InputLabel id="res-int-label" sx={{ color: 'text.secondary' }}>Select Integration Endpoint</InputLabel>
                <Select
                  labelId="res-int-label"
                  value={integrationId}
                  label="Select Integration Endpoint"
                  onChange={(e) => setIntegrationId(e.target.value)}
                  sx={{ color: '#fff', '.MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
                >
                  <MenuItem value="">-- Select Provider --</MenuItem>
                  {integrations.map(i => (
                    <MenuItem key={i.id} value={i.id}>
                      {i.integrationKey} ({i.providerType})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <TextField
                fullWidth size="small" label="Response Path Mapping (Dotted / Alias)"
                placeholder="customer.profile.age"
                value={responseMapping}
                onChange={(e) => setResponseMapping(e.target.value)}
                helperText="Path to select value from JSON response (e.g. customer.age) or Column alias for SQL (e.g. AGE)"
                slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '13px' } } }}
                sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
              />
            </>
          )}

          {/* Derived Logic SpEL */}
          {resolutionType === 'DERIVED' && (
            <TextField
              fullWidth multiline rows={3}
              label="Derived SpEL Expression"
              placeholder="context['currentYear'] - context['birthYear']"
              value={expression}
              onChange={(e) => setExpression(e.target.value)}
              helperText="SpEL formula. References to other context fields automatically triggers their lazy resolutions."
              slotProps={{ input: { sx: { color: '#fff', fontFamily: 'monospace', fontSize: '13px' } } }}
              sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
            />
          )}

          <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)' }} />

          {/* Caching & Cost controls */}
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            <FormControlLabel
              control={
                <Switch checked={cacheable} onChange={(e) => setCacheable(e.target.checked)} color="primary" size="small" />
              }
              label="Cache Resolution Result"
            />

            {cacheable && (
              <TextField
                size="small" label="TTL (Seconds)"
                type="number"
                value={ttlSeconds}
                onChange={(e) => setTtlSeconds(Number(e.target.value))}
                slotProps={{ input: { sx: { color: '#fff', width: 100 } } }}
                sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
              />
            )}
          </Box>

          <FormControl fullWidth size="small">
            <InputLabel id="res-cost-label" sx={{ color: 'text.secondary' }}>Evaluation Cost</InputLabel>
            <Select
              labelId="res-cost-label"
              value={cost}
              label="Evaluation Cost"
              onChange={(e) => setCost(e.target.value)}
              sx={{ color: '#fff', '.MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.1)' } }}
            >
              <MenuItem value="LOW">LOW (Static/Local parameters)</MenuItem>
              <MenuItem value="MEDIUM">MEDIUM (Cache/Light query)</MenuItem>
              <MenuItem value="HIGH">HIGH (External SOAP/REST API call)</MenuItem>
            </Select>
          </FormControl>

        </DialogContent>
        <DialogActions sx={{ p: 2.5, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
          <Button onClick={() => setResolutionDialogOpen(false)} sx={{ color: 'text.secondary' }}>
            Cancel
          </Button>
          <Button variant="contained" onClick={handleSaveResolution} sx={{ bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' } }}>
            Apply Resolution
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
