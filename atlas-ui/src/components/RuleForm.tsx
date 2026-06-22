import React, { useEffect, useState } from 'react';
import {
  Box, Typography, TextField, Button, Switch, FormControlLabel,
  CircularProgress, IconButton, Divider, Alert, Paper, Container
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import type { Rule } from '../store/workflowStore';

interface RuleFormProps {
  rule: Rule | null; // Null means create mode
  onClose: () => void;
  onRefresh: () => Promise<void>;
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const RuleForm: React.FC<RuleFormProps> = ({
  rule, onClose, onRefresh, onShowNotification
}) => {
  const [ruleKey, setRuleKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [expression, setExpression] = useState('');
  const [active, setActive] = useState(true);
  
  const [validating, setValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<{ valid: boolean; message: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (rule) {
      setRuleKey(rule.ruleKey);
      setName(rule.name);
      setDescription(rule.description || '');
      setExpression(rule.expression);
      setActive(rule.active);
    } else {
      setRuleKey('');
      setName('');
      setDescription('');
      setExpression('');
      setActive(true);
    }
    setValidationResult(null);
  }, [rule]);

  const handleValidate = async () => {
    if (!expression.trim()) {
      setValidationResult({ valid: false, message: 'expression cannot be empty.' });
      return;
    }
    setValidating(true);
    setValidationResult(null);
    try {
      const res = await fetch('/api/rules/validate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ expression: expression.trim() })
      });
      if (!res.ok) throw new Error('validation request failed');
      const data = await res.json();
      if (data.valid) {
        setValidationResult({ valid: true, message: 'expression syntax is valid!' });
      } else {
        setValidationResult({ valid: false, message: data.errors?.[0] || 'syntax error' });
      }
    } catch (err: any) {
      setValidationResult({ valid: false, message: err.message });
    } finally {
      setValidating(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ruleKey.trim() || !name.trim() || !expression.trim()) {
      onShowNotification('rule key, name, and expression are required fields.', 'error');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        ruleKey: ruleKey.trim().toUpperCase().replace(/\s+/g, '_'),
        name: name.trim(),
        description: description.trim(),
        expression: expression.trim(),
        active
      };

      const url = rule ? `/api/rules/${rule.id}` : '/api/rules';
      const method = rule ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'failed to save rule');
      }

      onShowNotification(rule ? 'rule updated!' : 'rule registered!', 'success');
      await onRefresh();
      onClose();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const accentColor = '#8b5cf6'; // Indigo/violet accent for Rule engine

  return (
    <Container maxWidth="md" sx={{ py: 2 }}>
      {/* Header / Navigation bar */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
        <IconButton onClick={onClose} sx={{ color: accentColor, border: `1px solid rgba(139,92,246,0.2)` }}>
          <ArrowBackIcon />
        </IconButton>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
            {rule ? 'modify rule' : 'register new rule'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            configure reusable expression rules for nodes.
          </Typography>
        </Box>
      </Box>

      {/* Main card form */}
      <Paper
        component="form"
        onSubmit={handleSubmit}
        elevation={0}
        sx={{
          p: 4,
          bgcolor: 'background.paper',
          border: `1px solid rgba(139,92,246,0.15)`,
          borderRadius: 3,
          boxShadow: '0 8px 32px rgba(0,0,0,0.1)',
          display: 'flex',
          flexDirection: 'column',
          gap: 3
        }}
      >
        {/* Active Status */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <FormControlLabel
            control={
              <Switch checked={active} onChange={(e) => setActive(e.target.checked)} color="secondary" />
            }
            label={active ? 'rule active' : 'rule suspended'}
            sx={{ '& .MuiFormControlLabel-label': { fontWeight: 700 } }}
          />
        </Box>

        {/* Rule Key */}
        <TextField
          fullWidth size="small" label="Rule Business Key"
          placeholder="RULE_HIGH_VAL"
          value={ruleKey}
          onChange={(e) => setRuleKey(e.target.value)}
          disabled={!!rule} // Key is immutable after registration
          helperText="uppercase unique identifier used in flow diagram configuration"
          slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Name */}
        <TextField
          fullWidth size="small" label="Rule Display Name"
          placeholder="High Value Amount Check"
          value={name}
          onChange={(e) => setName(e.target.value)}
          slotProps={{ input: { sx: { color: 'text.primary' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Description */}
        <TextField
          fullWidth size="small" label="Description"
          placeholder="Checks if the transaction amount is greater than 10,000"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          multiline rows={3}
          slotProps={{ input: { sx: { color: 'text.primary' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)' }} />

        {/* SpEL Expression */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
            <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary' }}>
              spel expression code
            </Typography>
            <Button
              variant="outlined" size="small" onClick={handleValidate} disabled={validating}
              sx={{
                color: accentColor, borderColor: `${accentColor}40`,
                fontSize: '11px', py: 0.4, px: 2, textTransform: 'lowercase',
                fontWeight: 700,
                borderRadius: 2,
                '&:hover': { borderColor: accentColor, bgcolor: `${accentColor}10` }
              }}
            >
              {validating ? <CircularProgress size={12} color="inherit" /> : 'validate syntax'}
            </Button>
          </Box>
          <TextField
            fullWidth multiline rows={4}
            placeholder="context['amount'] > 10000"
            value={expression}
            onChange={(e) => {
              setExpression(e.target.value);
              setValidationResult(null);
            }}
            slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '13px' } } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
          />
          <Typography variant="caption" color="text.secondary">
            Write expressions in terms of <code>context['key']</code>, e.g., <code>context['amount'] &gt; 5000</code>.
          </Typography>

          {/* Validation Banner */}
          {validationResult && (
            <Alert
              severity={validationResult.valid ? 'success' : 'error'}
              icon={validationResult.valid ? <CheckCircleIcon /> : <ErrorIcon />}
              sx={{
                borderRadius: 2,
                fontSize: '12px',
                py: 0.5,
                bgcolor: validationResult.valid ? 'rgba(34, 197, 94, 0.08)' : 'rgba(239, 68, 68, 0.08)',
                color: validationResult.valid ? '#22c55e' : '#ef4444',
                border: validationResult.valid ? '1px solid rgba(34,197,94,0.2)' : '1px solid rgba(239,68,68,0.2)',
                '& .MuiAlert-icon': { color: 'inherit' }
              }}
            >
              {validationResult.message}
            </Alert>
          )}
        </Box>

        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', my: 1 }} />

        {/* Footer Actions */}
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
          <Button
            variant="outlined"
            onClick={onClose}
            sx={{
              borderColor: 'divider',
              color: 'text.secondary',
              fontWeight: 700,
              textTransform: 'lowercase',
              borderRadius: 2,
              px: 3,
              '&:hover': {
                bgcolor: 'action.hover',
                borderColor: 'text.secondary'
              }
            }}
          >
            cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={submitting}
            startIcon={submitting ? <CircularProgress size={14} color="inherit" /> : <SaveIcon />}
            sx={{
              background: `linear-gradient(135deg, ${accentColor} 0%, ${accentColor}cc 100%)`,
              boxShadow: `0 4px 12px ${accentColor}40`,
              fontWeight: 700,
              textTransform: 'lowercase',
              borderRadius: 2,
              px: 4,
              '&:hover': { background: accentColor }
            }}
          >
            {submitting ? 'saving...' : 'save rule'}
          </Button>
        </Box>
      </Paper>
    </Container>
  );
};
