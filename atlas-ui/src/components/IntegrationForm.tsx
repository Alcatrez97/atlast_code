import React, { useEffect, useState } from 'react';
import {
  Box, Typography, TextField, Button, MenuItem, Select,
  FormControl, InputLabel, CircularProgress, IconButton, Divider, Paper, Container
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import type { Integration } from '../store/workflowStore';

interface IntegrationFormProps {
  integration: Integration | null; // Null means create mode
  onClose: () => void;
  onRefresh: () => Promise<void>;
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const IntegrationForm: React.FC<IntegrationFormProps> = ({
  integration, onClose, onRefresh, onShowNotification
}) => {
  const [integrationKey, setIntegrationKey] = useState('');
  const [name, setName] = useState('');
  const [providerType, setProviderType] = useState('REST');
  const [endpointUrl, setEndpointUrl] = useState('');
  const [method, setMethod] = useState('GET');
  const [headersJson, setHeadersJson] = useState('{}');
  const [requestTemplate, setRequestTemplate] = useState('');
  const [timeoutMs, setTimeoutMs] = useState(5000);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (integration) {
      setIntegrationKey(integration.integrationKey);
      setName(integration.name);
      setProviderType(integration.providerType);
      setEndpointUrl(integration.endpointUrl || '');
      setMethod(integration.method || 'GET');
      setHeadersJson(integration.headersJson || '{}');
      setRequestTemplate(integration.requestTemplate || '');
      setTimeoutMs(integration.timeoutMs || 5000);
    } else {
      setIntegrationKey('');
      setName('');
      setProviderType('REST');
      setEndpointUrl('');
      setMethod('GET');
      setHeadersJson('{}');
      setRequestTemplate('');
      setTimeoutMs(5000);
    }
  }, [integration]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!integrationKey.trim() || !name.trim()) {
      onShowNotification('integration key and display name are required fields.', 'error');
      return;
    }

    // Basic JSON check for headers
    if (providerType === 'REST' && headersJson) {
      try {
        JSON.parse(headersJson);
      } catch (err) {
        onShowNotification('http headers must be in valid json format.', 'error');
        return;
      }
    }

    setSubmitting(true);
    try {
      const payload = {
        id: integration?.id,
        integrationKey: integrationKey.trim().toUpperCase().replace(/\s+/g, '_'),
        name: name.trim(),
        providerType,
        endpointUrl: providerType === 'REST' ? endpointUrl.trim() : null,
        method: providerType === 'REST' ? method : null,
        headersJson: providerType === 'REST' ? headersJson.trim() : null,
        requestTemplate: requestTemplate.trim(),
        timeoutMs
      };

      const res = await fetch('/api/integrations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'failed to save integration endpoint');
      }

      onShowNotification(integration ? 'integration updated!' : 'integration registered!', 'success');
      await onRefresh();
      onClose();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const accentColor = '#14b8a6'; // Teal/cyan accent for integrations

  return (
    <Container maxWidth="md" sx={{ py: 2 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
        <IconButton onClick={onClose} sx={{ color: accentColor, border: `1px solid rgba(20,184,166,0.2)` }}>
          <ArrowBackIcon />
        </IconButton>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
            {integration ? 'modify integration' : 'register new integration'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            configure external endpoints for dynamic context loading.
          </Typography>
        </Box>
      </Box>

      {/* Form Card */}
      <Paper
        component="form"
        onSubmit={handleSubmit}
        elevation={0}
        sx={{
          p: 4,
          bgcolor: 'background.paper',
          border: `1px solid rgba(20,184,166,0.15)`,
          borderRadius: 3,
          boxShadow: '0 8px 32px rgba(0,0,0,0.1)',
          display: 'flex',
          flexDirection: 'column',
          gap: 3
        }}
      >
        {/* Key */}
        <TextField
          fullWidth size="small" label="Integration Key"
          placeholder="CRM_AON_PROVIDER"
          value={integrationKey}
          onChange={(e) => setIntegrationKey(e.target.value)}
          disabled={!!integration} // Key is immutable after registration
          helperText="uppercase unique key reference linked by context schema fields"
          slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Name */}
        <TextField
          fullWidth size="small" label="Display Name"
          placeholder="CRM Age on Network fetcher"
          value={name}
          onChange={(e) => setName(e.target.value)}
          slotProps={{ input: { sx: { color: 'text.primary' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Provider Type */}
        <FormControl fullWidth size="small">
          <InputLabel id="provider-type-label" sx={{ color: 'text.secondary' }}>Provider Type</InputLabel>
          <Select
            labelId="provider-type-label"
            value={providerType}
            label="Provider Type"
            onChange={(e) => setProviderType(e.target.value)}
            sx={{
              color: 'text.primary',
              '.MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
            }}
          >
            <MenuItem value="REST">REST API Endpoint</MenuItem>
            <MenuItem value="DB">SQL Database Query</MenuItem>
          </Select>
        </FormControl>

        {/* Timeout */}
        <TextField
          fullWidth size="small" label="Timeout (Milliseconds)"
          type="number"
          placeholder="5000"
          value={timeoutMs}
          onChange={(e) => setTimeoutMs(Number(e.target.value))}
          slotProps={{ input: { sx: { color: 'text.primary' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', my: 0.5 }} />

        {/* REST Fields */}
        {providerType === 'REST' && (
          <>
            <TextField
              fullWidth size="small" label="Endpoint HTTP URL"
              placeholder="http://localhost:9091/api/mock/customer/{{msisdn}}"
              value={endpointUrl}
              onChange={(e) => setEndpointUrl(e.target.value)}
              helperText="use {{varKey}} placeholders for parameter interpolation"
              slotProps={{ input: { sx: { color: 'text.primary', fontSize: '13px' } } }}
              sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
            />

            <FormControl fullWidth size="small">
              <InputLabel id="method-label" sx={{ color: 'text.secondary' }}>HTTP Method</InputLabel>
              <Select
                labelId="method-label"
                value={method}
                label="HTTP Method"
                onChange={(e) => setMethod(e.target.value)}
                sx={{
                  color: 'text.primary',
                  '.MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
                }}
              >
                <MenuItem value="GET">GET</MenuItem>
                <MenuItem value="POST">POST</MenuItem>
              </Select>
            </FormControl>

            <TextField
              fullWidth multiline rows={3}
              label="HTTP Headers (JSON)"
              placeholder='{"Authorization": "Bearer JWTTOKEN"}'
              value={headersJson}
              onChange={(e) => setHeadersJson(e.target.value)}
              helperText="valid json object of HTTP headers"
              slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '12px' } } }}
              sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
            />

            {method === 'POST' && (
              <TextField
                fullWidth multiline rows={4}
                label="POST Request Body Template"
                placeholder='{"msisdn": "{{msisdn}}", "circle": "{{circle}}"}'
                value={requestTemplate}
                onChange={(e) => setRequestTemplate(e.target.value)}
                helperText="JSON body template with dynamic {{varKey}} placeholders"
                slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '12px' } } }}
                sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
              />
            )}
          </>
        )}

        {/* Database Fields */}
        {providerType === 'DB' && (
          <TextField
            fullWidth multiline rows={5}
            label="SQL Query Template"
            placeholder="SELECT age FROM customer WHERE msisdn = {{msisdn}}"
            value={requestTemplate}
            onChange={(e) => setRequestTemplate(e.target.value)}
            helperText="SQL parameterized query. Placeholders like {{msisdn}} are mapped to prepared statement binds."
            slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '13px' } } }}
            sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
          />
        )}

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
            {submitting ? 'saving...' : 'save integration'}
          </Button>
        </Box>
      </Paper>
    </Container>
  );
};
