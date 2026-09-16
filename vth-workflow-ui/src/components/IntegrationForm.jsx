import React, { useEffect, useState } from 'react';
import { Box, Typography, TextField, Button, MenuItem, Select, FormControl, InputLabel, CircularProgress, IconButton, Divider, Paper, Container } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
export const IntegrationForm = ({ integration, onClose, onRefresh, onShowNotification }) => {
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
        }
        else {
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
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!integrationKey.trim() || !name.trim()) {
            onShowNotification('integration key and display name are required fields.', 'error');
            return;
        }
        // Basic JSON check for headers
        if (providerType === 'REST' && headersJson) {
            try {
                JSON.parse(headersJson);
            }
            catch (err) {
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
        }
        catch (err) {
            onShowNotification(err.message, 'error');
        }
        finally {
            setSubmitting(false);
        }
    };
    const accentColor = '#14b8a6'; // Teal/cyan accent for integrations
    return (<Container maxWidth={false} sx={{ px: { xs: 2, sm: 3, md: 4 }, py: 2 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
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
      <Paper component="form" onSubmit={handleSubmit} elevation={0} sx={{
            p: 4,
            bgcolor: 'background.paper',
            border: `1px solid rgba(20,184,166,0.15)`,
            borderRadius: 3,
            boxShadow: '0 8px 32px rgba(0,0,0,0.1)',
            display: 'flex',
            flexDirection: 'column',
            gap: 3
        }}>
        {/* Key */}
        <TextField fullWidth size="small" label="Integration Key" placeholder="CRM_AON_PROVIDER" value={integrationKey} onChange={(e) => setIntegrationKey(e.target.value)} disabled={!!integration} // Key is immutable after registration
     helperText="uppercase unique key reference linked by context schema fields" slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace' } } }} sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}/>

        {/* Name */}
        <TextField fullWidth size="small" label="Display Name" placeholder="CRM Age on Network fetcher" value={name} onChange={(e) => setName(e.target.value)} slotProps={{ input: { sx: { color: 'text.primary' } } }} sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}/>

        {/* Provider Type */}
        <FormControl fullWidth size="small">
          <InputLabel id="provider-type-label" sx={{ color: 'text.secondary' }}>Provider Type</InputLabel>
          <Select labelId="provider-type-label" value={providerType} label="Provider Type" onChange={(e) => setProviderType(e.target.value)} sx={{
            color: 'text.primary',
            '.MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
        }}>
            <MenuItem value="REST">REST API Endpoint</MenuItem>
            <MenuItem value="DB">SQL Database Query</MenuItem>
          </Select>
        </FormControl>

        {/* Timeout */}
        <TextField fullWidth size="small" label="Timeout (Milliseconds)" type="number" placeholder="5000" value={timeoutMs} onChange={(e) => setTimeoutMs(Number(e.target.value))} slotProps={{ input: { sx: { color: 'text.primary' } } }} sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}/>

        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', my: 0.5 }}/>

        {/* REST Fields */}
        {providerType === 'REST' && (<>
            {/* Context Data Flow Info Banner */}
            <Paper elevation={0} sx={{ p: 2, bgcolor: 'rgba(20,184,166,0.05)', border: '1px solid rgba(20,184,166,0.2)', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800, color: '#14b8a6', letterSpacing: 0.3, textTransform: 'uppercase', fontSize: '11px' }}>
                  🔄 Context &amp; Payload Data Flow Guide
                </Typography>
              </Box>
              <Typography variant="body2" sx={{ fontSize: '12px', color: 'text.secondary', mb: 1.5, lineHeight: 1.5 }}>
                External integrations communicate with workflow context in two directions:
              </Typography>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 1.5 }}>
                <Box sx={{ p: 1.5, bgcolor: 'background.default', borderRadius: 1.5, border: '1px solid rgba(255,255,255,0.06)' }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: '#38bdf8', display: 'block', mb: 0.5 }}>
                    📤 Outgoing: Context ➔ API Request
                  </Typography>
                  <Typography variant="caption" sx={{ fontSize: '11px', color: 'text.secondary', display: 'block', lineHeight: 1.4 }}>
                    Use <code>&#123;&#123;fieldName&#125;&#125;</code> in the <b>URL</b>, <b>Headers</b>, or <b>Body Template</b>. At runtime, values are automatically interpolated from active workflow context.
                  </Typography>
                </Box>
                <Box sx={{ p: 1.5, bgcolor: 'background.default', borderRadius: 1.5, border: '1px solid rgba(255,255,255,0.06)' }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: '#10b981', display: 'block', mb: 0.5 }}>
                    📥 Incoming: API Response ➔ Context
                  </Typography>
                  <Typography variant="caption" sx={{ fontSize: '11px', color: 'text.secondary', display: 'block', lineHeight: 1.4 }}>
                    In <b>Context Schema</b>: set <code>Response Path Mapping</code> (e.g. <code>data.score</code>).<br/>
                    In <b>Command Nodes</b>: set <code>Response Output Mapping</code> (e.g. <code>&#123;&quot;score&quot;: &quot;context.score&quot;&#125;</code>).
                  </Typography>
                </Box>
              </Box>
            </Paper>

            <TextField fullWidth size="small" label="Endpoint HTTP URL" placeholder="http://localhost:9091/api/kyc/verify/{{msisdn}}?pan={{panNumber}}" value={endpointUrl} onChange={(e) => setEndpointUrl(e.target.value)} helperText="Supports dynamic {{varKey}} placeholders interpolated from context (e.g., {{msisdn}}, {{panNumber}})" slotProps={{ input: { sx: { color: 'text.primary', fontSize: '13px', fontFamily: 'monospace' } } }} sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}/>

            <FormControl fullWidth size="small">
              <InputLabel id="method-label" sx={{ color: 'text.secondary' }}>HTTP Method</InputLabel>
              <Select labelId="method-label" value={method} label="HTTP Method" onChange={(e) => setMethod(e.target.value)} sx={{
                color: 'text.primary',
                '.MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
            }}>
                <MenuItem value="GET">GET (Pass parameters in URL query or path)</MenuItem>
                <MenuItem value="POST">POST (Pass payload via Request Body Template)</MenuItem>
                <MenuItem value="PUT">PUT (Replace resource via Request Body Template)</MenuItem>
                <MenuItem value="PATCH">PATCH (Update partial resource via Request Body Template)</MenuItem>
                <MenuItem value="DELETE">DELETE (Delete resource)</MenuItem>
              </Select>
            </FormControl>

            <TextField fullWidth multiline rows={2} label="HTTP Headers (JSON)" placeholder='{"Authorization": "Bearer {{authToken}}", "Content-Type": "application/json"}' value={headersJson} onChange={(e) => setHeadersJson(e.target.value)} helperText="Valid JSON object of HTTP headers. Supports dynamic {{varKey}} placeholders." slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '12px' } } }} sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}/>

            {['POST', 'PUT', 'PATCH'].includes(method) && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                    📤 Outbound Request Body Template ({method})
                  </Typography>
                  <Button
                    size="small"
                    variant="text"
                    onClick={() => setRequestTemplate(JSON.stringify({
                      pan: "{{panNumber}}",
                      msisdn: "{{msisdn}}",
                      circle: "{{circleId}}",
                      referenceId: "{{businessKey}}"
                    }, null, 2))}
                    sx={{ fontSize: '11px', textTransform: 'none', color: '#14b8a6', py: 0 }}
                  >
                    Insert Sample Outgoing Payload
                  </Button>
                </Box>
                <TextField fullWidth multiline rows={5} label="" placeholder={`{\n  "pan": "{{panNumber}}",\n  "circle": "{{circleId}}",\n  "amount": "{{orderAmount}}"\n}`} value={requestTemplate} onChange={(e) => setRequestTemplate(e.target.value)} helperText="JSON payload template. Placeholders like {{varKey}} will be dynamically replaced with values from workflow context." slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '12px' } } }} sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}/>
              </Box>
            )}
          </>)}

        {/* Database Fields */}
        {providerType === 'DB' && (<TextField fullWidth multiline rows={5} label="SQL Query Template" placeholder="SELECT age FROM customer WHERE msisdn = {{msisdn}}" value={requestTemplate} onChange={(e) => setRequestTemplate(e.target.value)} helperText="SQL parameterized query. Placeholders like {{msisdn}} are mapped to prepared statement binds." slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '13px' } } }} sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}/>)}

        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', my: 1 }}/>

        {/* Footer Actions */}
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
          <Button variant="outlined" onClick={onClose} sx={{
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
        }}>
            cancel
          </Button>
          <Button type="submit" variant="contained" disabled={submitting} startIcon={submitting ? <CircularProgress size={14} color="inherit"/> : <SaveIcon />} sx={{
            background: `linear-gradient(135deg, ${accentColor} 0%, ${accentColor}cc 100%)`,
            boxShadow: `0 4px 12px ${accentColor}40`,
            fontWeight: 700,
            textTransform: 'lowercase',
            borderRadius: 2,
            px: 4,
            '&:hover': { background: accentColor }
        }}>
            {submitting ? 'saving...' : 'save integration'}
          </Button>
        </Box>
      </Paper>
    </Container>);
};
