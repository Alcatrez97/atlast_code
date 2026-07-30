import React, { useState, useEffect } from 'react';
import { Drawer, Box, Typography, IconButton, TextField, Button, Chip, CircularProgress, Alert, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import SaveIcon from '@mui/icons-material/Save';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoIcon from '@mui/icons-material/Info';
const ACCENT_COLOR = '#818cf8'; // Premium light indigo for edges
export const EdgePropertiesDrawer = ({ open, edge, nodes, isReadOnly, onClose, onSaveEdge, onDeleteEdge }) => {
    const [label, setLabel] = useState('');
    const [condition, setCondition] = useState('');
    const [outcomeType, setOutcomeType] = useState('');
    const [outcomes, setOutcomes] = useState([]);
    const [sourceNode, setSourceNode] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    // Populate fields and fetch outcomes when edge changes
    useEffect(() => {
        if (edge) {
            setLabel(edge.label || '');
            setCondition(edge.data?.condition || '');
            setOutcomeType(edge.data?.outcomeType || '');
            setSaved(false);
            if (nodes) {
                const srcNode = nodes.find(n => n.id === edge.source);
                setSourceNode(srcNode);
                if (srcNode && srcNode.type === 'BUCKET') {
                    const bucketId = srcNode.data?.bucketId || srcNode.id;
                    fetch(`/api/buckets/key/${bucketId}/outcomes`)
                        .then(res => res.ok ? res.json() : [])
                        .then(data => setOutcomes(data || []))
                        .catch(() => setOutcomes([{ name: 'Accept' }, { name: 'Reject' }]));
                }
                else {
                    setOutcomes([]);
                }
            }
            else {
                setSourceNode(null);
                setOutcomes([]);
            }
        }
        else {
            setLabel('');
            setCondition('');
            setOutcomeType('');
            setOutcomes([]);
            setSourceNode(null);
        }
    }, [edge, nodes]);
    const handleOutcomeChange = (val) => {
        setOutcomeType(val);
        if (val && (!label || label === '' || outcomes.some(o => o.name === label))) {
            setLabel(val);
        }
    };
    const handleSave = async () => {
        if (!edge || !onSaveEdge)
            return;
        setIsSaving(true);
        try {
            await onSaveEdge(edge.id, { label, condition, outcomeType });
            setSaved(true);
            setTimeout(() => setSaved(false), 2000);
        }
        finally {
            setIsSaving(false);
        }
    };
    return (<Drawer anchor="right" open={open} onClose={onClose} variant="persistent" slotProps={{
            paper: {
                sx: {
                    width: 340,
                    bgcolor: 'background.paper',
                    borderLeft: `1px solid ${ACCENT_COLOR}30`,
                    boxShadow: `-8px 0 32px rgba(0,0,0,0.2)`,
                    color: 'text.primary'
                }
            }
        }}>
      {edge && (<Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header */}
          <Box sx={{
                display: 'flex', alignItems: 'center', p: 2,
                borderBottom: `1px solid ${ACCENT_COLOR}20`,
                background: `linear-gradient(135deg, ${ACCENT_COLOR}10 0%, transparent 100%)`
            }}>
            <Box sx={{
                width: 32, height: 32, borderRadius: 2,
                bgcolor: ACCENT_COLOR + '20',
                border: `1px solid ${ACCENT_COLOR}40`,
                display: 'flex', alignItems: 'center', justifyContent: 'center', mr: 1.5
            }}>
              <EditIcon sx={{ fontSize: 16, color: ACCENT_COLOR }}/>
            </Box>
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                {edge.label || 'Connection Edge'}
              </Typography>
              <Box sx={{ display: 'flex', gap: 0.5, mt: 0.25 }}>
                <Chip label="EDGE" size="small" sx={{ height: 16, fontSize: '8px', fontWeight: 800, bgcolor: ACCENT_COLOR + '20', color: ACCENT_COLOR, border: 'none' }}/>
                {isReadOnly && <Chip label="READ ONLY" size="small" sx={{ height: 16, fontSize: '8px', bgcolor: 'rgba(255,255,255,0.05)', color: 'text.secondary', border: 'none' }}/>}
              </Box>
            </Box>
            <IconButton size="small" onClick={onClose}><CloseIcon fontSize="small"/></IconButton>
          </Box>

          <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 2, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <Box>
              <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: 1, display: 'block', mb: 1 }}>
                EDGE PROPERTIES
              </Typography>
              <Typography variant="caption" sx={{ fontFamily: 'monospace', color: '#6b7280', display: 'block', mb: 1.5, wordBreak: 'break-all' }}>
                ID: {edge.id}
              </Typography>

              {/* Label */}
              <TextField fullWidth size="small" label="Label" value={label} onChange={(e) => setLabel(e.target.value)} disabled={isReadOnly} placeholder="e.g. APPROVED, true, false" helperText="Text visible directly on the connection line" sx={{ mb: 2.5 }}/>

              {/* Outcome Type */}
              {sourceNode?.type === 'BUCKET' && (<FormControl fullWidth size="small" sx={{ mb: 2.5 }}>
                  <InputLabel id="outcome-type-select-label">Outcome Type</InputLabel>
                  <Select labelId="outcome-type-select-label" label="Outcome Type" value={outcomeType} onChange={(e) => handleOutcomeChange(e.target.value)} disabled={isReadOnly}>
                    <MenuItem value="">
                      <em>None (Unconditional / SpEL fallback)</em>
                    </MenuItem>
                    {outcomes.map(o => (<MenuItem key={o.name} value={o.name}>
                        {o.name} {o.formStatus ? `(${o.formStatus})` : ''}
                      </MenuItem>))}
                  </Select>
                </FormControl>)}

              {/* Condition */}
              <TextField fullWidth size="small" label="Routing Condition" value={condition} onChange={(e) => setCondition(e.target.value)} disabled={isReadOnly} placeholder="e.g. true, false, HIGH, context['amount'] > 10000" helperText="Condition evaluated by state machine traversal to route transactions" slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '12px' } } }} sx={{ mb: 2.5 }}/>

              {/* Help Box */}
              <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.06)' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.75 }}>
                  <InfoIcon sx={{ fontSize: 14, color: 'text.secondary' }}/>
                  <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary' }}>
                    ROUTING LOGIC GUIDE
                  </Typography>
                </Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.4 }}>
                  • <strong>Rule Nodes</strong> check for matching <code>"true"</code> or <code>"false"</code> conditions, or evaluate SpEL conditions on the edge.
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, lineHeight: 1.4 }}>
                  • <strong>Decision Nodes</strong> look for an exact string equality match of the Decision Field value (e.g., <code>"APPROVED"</code>, <code>"HIGH"</code>) or evaluate SpEL conditions.
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5, lineHeight: 1.4 }}>
                  • If no conditions match, the traversal falls back to the first unconditional edge.
                </Typography>
              </Box>
            </Box>
          </Box>

          {/* Footer */}
          {!isReadOnly && (<Box sx={{ p: 2, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', flexDirection: 'column', gap: 1 }}>
              {saved && <Alert severity="success" sx={{ mb: 1, borderRadius: 1, py: 0.5 }}>Saved!</Alert>}
              <Button fullWidth variant="contained" startIcon={isSaving ? <CircularProgress size={14} color="inherit"/> : <SaveIcon />} onClick={handleSave} disabled={isSaving} sx={{
                    background: `linear-gradient(135deg, ${ACCENT_COLOR} 0%, ${ACCENT_COLOR}cc 100%)`,
                    boxShadow: `0 4px 12px ${ACCENT_COLOR}40`,
                    fontWeight: 700,
                }}>
                {isSaving ? 'Saving...' : 'Save Edge'}
              </Button>
              {onDeleteEdge && (<Button fullWidth variant="outlined" color="error" startIcon={<DeleteIcon />} onClick={() => onDeleteEdge(edge.id)} sx={{
                        borderColor: 'rgba(244,67,54,0.3)',
                        color: '#f44336',
                        '&:hover': {
                            borderColor: '#f44336',
                            bgcolor: 'rgba(244,67,54,0.04)',
                        }
                    }}>
                  Delete Edge
                </Button>)}
            </Box>)}
        </Box>)}
    </Drawer>);
};
