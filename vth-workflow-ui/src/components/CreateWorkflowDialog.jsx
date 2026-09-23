import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  RadioGroup,
  Radio,
  FormControlLabel,
  FormControl,
  FormLabel,
  Select,
  MenuItem,
  Checkbox,
  ListItemText,
  Chip,
  OutlinedInput,
  Typography,
  Paper
} from '@mui/material';
import GlobeIcon from '@mui/icons-material/Public';
import CircleIcon from '@mui/icons-material/TripOrigin';

const CIRCLE_OPTIONS = [
  { id: 101, label: '101 - MH (Maharashtra)', badge: 'MH', color: '#00A05A' },
  { id: 102, label: '102 - DL (Delhi)', badge: 'DL', color: '#0CADEF' },
  { id: 103, label: '103 - KA (Karnataka)', badge: 'KA', color: '#8b5cf6' },
  { id: 104, label: '104 - TN (Tamil Nadu)', badge: 'TN', color: '#ff6d00' },
  { id: 105, label: '105 - DL_NCR (Delhi NCR)', badge: 'NCR', color: '#d97706' },
  { id: 106, label: '106 - ROWB (West Bengal)', badge: 'WB', color: '#64748b' },
];

export const STANDARD_CONVENTIONS = [
  { prefix: 'CAF_', domain: 'Retail Postpaid (CAF)', table: 'POSTPAID_ONBOARD_CAF', statusCol: 'form_status', pk: 'caf_id' },
  { prefix: 'COCP_', domain: 'Corporate Postpaid (COCP)', table: 'POSTPAID_ONBOARD_COCP', statusCol: 'form_status', pk: 'cocp_id' },
];

export const CreateWorkflowDialog = ({ open, onClose, onSubmit, initialData = null, mode = 'create' }) => {
    const [name, setName] = useState('');
    const [key, setKey] = useState('');
    const [description, setDescription] = useState('');
    const [scopeMode, setScopeMode] = useState('ALL'); // 'ALL' or 'SPECIFIC'
    const [selectedCircles, setSelectedCircles] = useState([]);
    const [errors, setErrors] = useState({});

    const matchedConvention = STANDARD_CONVENTIONS.find(c => key.toUpperCase().startsWith(c.prefix));

    useEffect(() => {
        if (open) {
            setName(initialData?.name || '');
            setKey(initialData?.key || '');
            setDescription(initialData?.description || '');
            setErrors({});

            const rawCircleId = initialData?.circleId;
            if (rawCircleId == null || rawCircleId === 'ALL') {
                setScopeMode('ALL');
                setSelectedCircles([]);
            } else {
                setScopeMode('SPECIFIC');
                setSelectedCircles([Number(rawCircleId)]);
            }
        }
    }, [open, initialData]);

    const handleFormSubmit = () => {
        const newErrors = {};
        if (!name.trim())
            newErrors.name = 'Workflow name is required';
        if (!key.trim()) {
            newErrors.key = 'Workflow key is required';
        }
        else if (!/^[a-zA-Z0-9_-]+$/.test(key)) {
            newErrors.key = 'Key must be alphanumeric with no spaces (e.g. Order_Validation)';
        }

        if (scopeMode === 'SPECIFIC' && selectedCircles.length === 0) {
            newErrors.circleId = 'Please select a circle or switch to All Circles.';
        }

        if (Object.keys(newErrors).length > 0) {
            setErrors(newErrors);
            return;
        }

        const finalCircleId = scopeMode === 'ALL' ? null : (selectedCircles.length > 0 ? Number(selectedCircles[0]) : null);
        onSubmit({ name, key, description, circleId: finalCircleId });
        onClose();
    };

    const handleCircleChange = (event) => {
        const { target: { value } } = event;
        setSelectedCircles(typeof value === 'string' ? value.split(',') : value);
        if (errors.circleId) {
            setErrors(prev => ({ ...prev, circleId: null }));
        }
    };

    const handleSelectAll = () => {
        setSelectedCircles(CIRCLE_OPTIONS.map(c => c.id));
        if (errors.circleId) setErrors(prev => ({ ...prev, circleId: null }));
    };

    const handleClearAll = () => {
        setSelectedCircles([]);
    };

    return (
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, borderBottom: '1px solid', borderColor: 'divider', pb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
          {mode === 'copy' ? 'Copy Workflow Definition' : 'Create Workflow Definition'}
        </DialogTitle>
        <DialogContent sx={{ mt: 2 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <TextField
              autoFocus
              label="Workflow Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              error={!!errors.name}
              helperText={errors.name}
              fullWidth
              required
              variant="outlined"
              placeholder="e.g. Order Validation Pipeline"
            />
            <TextField
              label="Workflow Key"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              error={!!errors.key}
              helperText={errors.key || "System-wide unique identifier (alphanumeric, no spaces)"}
              fullWidth
              required
              variant="outlined"
              placeholder="e.g. ORDER_VALIDATION_PIPELINE"
            />

            {/* Quick Domain Prefix Selectors */}
            <Box sx={{ mt: -1, mb: 0.5 }}>
              <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600, display: 'block', mb: 0.5, fontSize: '11px' }}>
                Naming Convention Prefix (Determines Target Table & Field):
              </Typography>
              <Box sx={{ display: 'flex', gap: 0.8, flexWrap: 'wrap' }}>
                {STANDARD_CONVENTIONS.map(c => {
                  const isSelected = key.toUpperCase().startsWith(c.prefix);
                  return (
                    <Chip
                      key={c.prefix}
                      label={c.prefix}
                      size="small"
                      clickable
                      color={isSelected ? 'primary' : 'default'}
                      variant={isSelected ? 'filled' : 'outlined'}
                      onClick={() => {
                        const stripped = key.replace(/^[A-Za-z0-9]+_/, '');
                        setKey(`${c.prefix}${stripped || 'WORKFLOW'}`);
                        if (errors.key) setErrors(prev => ({ ...prev, key: null }));
                      }}
                      sx={{ fontSize: '10px', fontWeight: 700, fontFamily: 'monospace', height: 22 }}
                    />
                  );
                })}
              </Box>
            </Box>

            {/* Live Domain Mapping & Warning Box */}
            {key.trim() && (
              <Box sx={{
                p: 1.5,
                borderRadius: 1.5,
                bgcolor: matchedConvention ? 'rgba(16, 185, 129, 0.08)' : 'rgba(245, 158, 11, 0.08)',
                border: `1px solid ${matchedConvention ? 'rgba(16, 185, 129, 0.3)' : 'rgba(245, 158, 11, 0.3)'}`,
                display: 'flex',
                flexDirection: 'column',
                gap: 0.5
              }}>
                <Typography variant="caption" sx={{
                  fontWeight: 800,
                  color: matchedConvention ? '#10b981' : '#d97706',
                  fontSize: '11px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5
                }}>
                  {matchedConvention 
                    ? `✅ Domain Convention Matched: [${matchedConvention.domain}]` 
                    : '⚠️ Naming Convention Notice: Non-Standard Prefix'}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '11px', lineHeight: 1.4 }}>
                  {matchedConvention ? (
                    <>
                      Workflows with prefix <code>{matchedConvention.prefix}</code> automatically synchronize bucket statuses to table{' '}
                      <b style={{ color: '#10b981' }}>{matchedConvention.table}</b> on column{' '}
                      <code>{matchedConvention.statusCol}</code> (PK: <code>{matchedConvention.pk}</code>).
                    </>
                  ) : (
                    <>
                      Key does not start with a recognized domain prefix (<code>CAF_</code>, <code>COCP_</code>).
                      Bucket stages will default to updating <b>POSTPAID_ONBOARD_CAF</b> unless an explicit table override is defined in the Context Schema.
                    </>
                  )}
                </Typography>
              </Box>
            )}

            <TextField
              label="Description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              fullWidth
              multiline
              rows={2}
              variant="outlined"
              placeholder="Describe the objective and execution plan of this workflow..."
            />

            {/* Circle Scope Configuration Section */}
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, bgcolor: 'background.default', border: errors.circleId ? '1px solid #ef4444' : '1px solid divider' }}>
              <FormControl component="fieldset" fullWidth>
                <FormLabel component="legend" sx={{ fontWeight: 600, fontSize: '13px', color: 'text.primary', mb: 1, display: 'flex', alignItems: 'center', gap: 0.8 }}>
                  <CircleIcon sx={{ fontSize: 16, color: 'primary.main' }} /> Circle Target Scope
                </FormLabel>
                
                <RadioGroup
                  row
                  value={scopeMode}
                  onChange={(e) => {
                    setScopeMode(e.target.value);
                    if (errors.circleId) setErrors(prev => ({ ...prev, circleId: null }));
                  }}
                  sx={{ mb: 1.5 }}
                >
                  <FormControlLabel
                    value="ALL"
                    control={<Radio size="small" />}
                    label={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <GlobeIcon sx={{ fontSize: 16, color: '#3b82f6' }} />
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>All Circles (Global)</Typography>
                      </Box>
                    }
                  />
                  <FormControlLabel
                    value="SPECIFIC"
                    control={<Radio size="small" />}
                    label={
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>Specific Circle(s)</Typography>
                    }
                  />
                </RadioGroup>

                {scopeMode === 'SPECIFIC' && (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mt: 0.5 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                        Select single or multiple circles:
                      </Typography>
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button size="small" variant="text" onClick={handleSelectAll} sx={{ p: 0, minWidth: 'auto', fontSize: '11px', fontWeight: 600 }}>
                          Select All
                        </Button>
                        <Typography variant="caption" color="text.disabled">|</Typography>
                        <Button size="small" variant="text" onClick={handleClearAll} sx={{ p: 0, minWidth: 'auto', fontSize: '11px', color: 'text.secondary' }}>
                          Clear
                        </Button>
                      </Box>
                    </Box>

                    <Select
                      multiple
                      fullWidth
                      size="small"
                      value={selectedCircles}
                      onChange={handleCircleChange}
                      input={<OutlinedInput placeholder="Select targeted circle(s)" />}
                      renderValue={(selected) => (
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                          {selected.map((circleId) => {
                            const opt = CIRCLE_OPTIONS.find(c => c.id === circleId) || { badge: circleId, color: '#6366f1' };
                            return (
                              <Chip
                                key={circleId}
                                label={opt.badge || circleId}
                                size="small"
                                sx={{
                                  height: 22,
                                  fontSize: '11px',
                                  fontWeight: 700,
                                  bgcolor: `${opt.color}15`,
                                  color: opt.color,
                                  border: `1px solid ${opt.color}40`
                                }}
                              />
                            );
                          })}
                        </Box>
                      )}
                    >
                      {CIRCLE_OPTIONS.map((opt) => (
                        <MenuItem key={opt.id} value={opt.id} sx={{ fontSize: '13px' }}>
                          <Checkbox checked={selectedCircles.indexOf(opt.id) > -1} size="small" />
                          <ListItemText primary={opt.label} primaryTypographyProps={{ fontSize: '13px', fontWeight: 500 }} />
                        </MenuItem>
                      ))}
                    </Select>
                    {errors.circleId && (
                      <Typography variant="caption" color="error" sx={{ mt: -0.5 }}>
                        {errors.circleId}
                      </Typography>
                    )}
                  </Box>
                )}
              </FormControl>
            </Paper>

          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 2.5, borderTop: '1px solid', borderColor: 'divider' }}>
          <Button onClick={onClose} variant="text" sx={{ color: 'text.secondary' }}>
            Cancel
          </Button>
          <Button onClick={handleFormSubmit} variant="contained" color="primary">
            {mode === 'copy' ? 'Copy Workflow' : 'Create Definition'}
          </Button>
        </DialogActions>
      </Dialog>
    );
};
