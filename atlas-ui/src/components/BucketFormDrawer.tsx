import React, { useEffect, useState } from 'react';
import {
  Drawer, Box, Typography, TextField, Button, RadioGroup, FormControlLabel,
  Radio, Switch, FormControl, InputLabel, Select, MenuItem, CircularProgress,
  IconButton, Divider
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import SaveIcon from '@mui/icons-material/Save';
import type { Bucket } from '../store/workflowStore';

interface BucketFormDrawerProps {
  open: boolean;
  bucket: Bucket | null; // Null means create mode
  onClose: () => void;
  onRefresh: () => Promise<void>;
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

const CATEGORIES = ['FRAUD', 'COMPLIANCE', 'SERVICING', 'ESCALATION', 'MARKETING', 'GENERAL'];

export const BucketFormDrawer: React.FC<BucketFormDrawerProps> = ({
  open, bucket, onClose, onRefresh, onShowNotification
}) => {
  const [bucketId, setBucketId] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('GENERAL');
  const [priority, setPriority] = useState('MEDIUM');
  const [slaHours, setSlaHours] = useState<number | ''>('');
  const [ownerGroup, setOwnerGroup] = useState('');
  const [autoActions, setAutoActions] = useState('');
  const [active, setActive] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (bucket) {
      setBucketId(bucket.bucketId);
      setName(bucket.name);
      setDescription(bucket.description || '');
      setCategory(bucket.category || 'GENERAL');
      setPriority(bucket.priority || 'MEDIUM');
      setSlaHours(bucket.slaHours !== undefined ? bucket.slaHours : '');
      setOwnerGroup(bucket.ownerGroup || '');
      setAutoActions(bucket.autoActions || '');
      setActive(bucket.active);
    } else {
      setBucketId('');
      setName('');
      setDescription('');
      setCategory('GENERAL');
      setPriority('MEDIUM');
      setSlaHours('');
      setOwnerGroup('');
      setAutoActions('');
      setActive(true);
    }
  }, [bucket, open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bucketId.trim() || !name.trim()) {
      onShowNotification('Bucket ID and Name are required fields.', 'error');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        bucketId: bucketId.trim().toUpperCase().replace(/\s+/g, '_'),
        name: name.trim(),
        description: description.trim(),
        category,
        priority,
        slaHours: slaHours === '' ? null : Number(slaHours),
        ownerGroup: ownerGroup.trim(),
        autoActions: autoActions.trim(),
        active
      };

      const url = bucket ? `/api/buckets/${bucket.id}` : '/api/buckets';
      const method = bucket ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to save bucket');
      }

      onShowNotification(bucket ? 'Bucket updated!' : 'Bucket registered!', 'success');
      await onRefresh();
      onClose();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const getPriorityColor = (p: string) => {
    switch (p) {
      case 'CRITICAL': return '#ef4444';
      case 'HIGH': return '#f97316';
      case 'MEDIUM': return '#eab308';
      case 'LOW': return '#22c55e';
      default: return '#94a3b8';
    }
  };

  const accentColor = getPriorityColor(priority);

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      slotProps={{
        paper: {
          sx: {
            width: 360,
            bgcolor: 'background.paper',
            borderLeft: `1px solid ${accentColor}30`,
            boxShadow: `-8px 0 32px rgba(0,0,0,0.2)`,
            color: 'text.primary'
          }
        }
      }}
    >
      <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header */}
        <Box sx={{
          display: 'flex', alignItems: 'center', p: 2,
          borderBottom: `1px solid ${accentColor}20`,
          background: `linear-gradient(135deg, ${accentColor}10 0%, transparent 100%)`
        }}>
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
              {bucket ? 'Modify Bucket' : 'Register New Bucket'}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Configure endpoints and operational ownership.
            </Typography>
          </Box>
          <IconButton size="small" onClick={onClose} sx={{ color: 'text.secondary' }}>
            <CloseIcon fontSize="small" />
          </IconButton>
        </Box>

        {/* Content */}
        <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 2.5, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          {/* Active Status */}
          <FormControlLabel
            control={
              <Switch checked={active} onChange={(e) => setActive(e.target.checked)} color="primary" />
            }
            label={active ? 'Bucket Active' : 'Bucket Suspended'}
          />

          {/* Bucket ID */}
          <TextField
            fullWidth size="small" label="Bucket Business ID"
            placeholder="BCK_FRAUD_ESCALATION"
            value={bucketId}
            onChange={(e) => setBucketId(e.target.value)}
            disabled={!!bucket} // Business ID immutable after registration
            helperText="Uppercase unique identifier used in flow diagram configuration"
            slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace' } } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
          />

          {/* Name */}
          <TextField
            fullWidth size="small" label="Outcome Display Name"
            placeholder="Fraud Escalation Queue"
            value={name}
            onChange={(e) => setName(e.target.value)}
            slotProps={{ input: { sx: { color: 'text.primary' } } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
          />

          {/* Category */}
          <FormControl fullWidth size="small">
            <InputLabel id="category-select-label" sx={{ color: 'text.secondary' }}>Category</InputLabel>
            <Select
              labelId="category-select-label"
              value={category}
              label="Category"
              onChange={(e) => setCategory(e.target.value)}
              sx={{ color: 'text.primary', '.MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
            >
              {CATEGORIES.map(cat => (
                <MenuItem key={cat} value={cat}>{cat}</MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Priority */}
          <Box>
            <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', display: 'block', mb: 1 }}>
              OPERATIONAL PRIORITY LEVEL
            </Typography>
            <RadioGroup
              row value={priority} onChange={(e) => setPriority(e.target.value)}
              sx={{ display: 'flex', gap: 1 }}
            >
              {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(lvl => (
                <FormControlLabel
                  key={lvl} value={lvl} control={<Radio size="small" />} label={lvl}
                  sx={{
                    m: 0, px: 1, borderRadius: 1.5,
                    border: priority === lvl ? `1px solid ${getPriorityColor(lvl)}` : '1px solid rgba(255,255,255,0.06)',
                    bgcolor: priority === lvl ? `${getPriorityColor(lvl)}10` : 'transparent',
                    color: priority === lvl ? getPriorityColor(lvl) : 'text.secondary',
                    fontSize: '11px'
                  }}
                />
              ))}
            </RadioGroup>
          </Box>

          <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)' }} />

          {/* SLA Hours */}
          <TextField
            fullWidth size="small" label="SLA Resolution Target (Hours)"
            placeholder="24" type="number"
            value={slaHours}
            onChange={(e) => setSlaHours(e.target.value === '' ? '' : Number(e.target.value))}
            helperText="Maximum allowed time to resolve cases routed here"
            slotProps={{ input: { sx: { color: 'text.primary' } } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
          />

          {/* Owner Group */}
          <TextField
            fullWidth size="small" label="Operational Owner Group"
            placeholder="Risk Operations Team B"
            value={ownerGroup}
            onChange={(e) => setOwnerGroup(e.target.value)}
            slotProps={{ input: { sx: { color: 'text.primary' } } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
          />

          {/* Auto Actions */}
          <TextField
            fullWidth size="small" label="Automatic Actions (Tags)"
            placeholder="SEND_EMAIL,TRIGGER_WEBHOOK"
            value={autoActions}
            onChange={(e) => setAutoActions(e.target.value)}
            helperText="Comma separated tags to trigger background tasks"
            slotProps={{ input: { sx: { color: 'text.primary', fontSize: '12px' } } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
          />
        </Box>

        {/* Footer */}
        <Box sx={{ p: 2, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
          <Button
            type="submit" fullWidth variant="contained"
            startIcon={submitting ? <CircularProgress size={14} color="inherit" /> : <SaveIcon />}
            disabled={submitting}
            sx={{
              background: `linear-gradient(135deg, ${accentColor} 0%, ${accentColor}cc 100%)`,
              boxShadow: `0 4px 12px ${accentColor}40`,
              fontWeight: 800,
            }}
          >
            {submitting ? 'Saving...' : 'Save Registry Entry'}
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
};
