import React, { useEffect, useState } from 'react';
import {
  Box, Typography, TextField, Button, Switch, FormControlLabel,
  CircularProgress, IconButton, Divider, Paper, Container
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import type { EventDefinition } from '../store/workflowStore';

interface EventFormProps {
  event: EventDefinition | null; // Null means create mode
  onClose: () => void;
  onRefresh: () => Promise<void>;
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const EventForm: React.FC<EventFormProps> = ({
  event, onClose, onRefresh, onShowNotification
}) => {
  const [eventKey, setEventKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [kafkaTopic, setKafkaTopic] = useState('');
  const [correlationKeyPath, setCorrelationKeyPath] = useState('');
  const [payloadSchema, setPayloadSchema] = useState('');
  const [active, setActive] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (event) {
      setEventKey(event.eventKey);
      setName(event.name || '');
      setDescription(event.description || '');
      setKafkaTopic(event.kafkaTopic || '');
      setCorrelationKeyPath(event.correlationKeyPath || '');
      setPayloadSchema(event.payloadSchema || '');
      setActive(event.active);
    } else {
      setEventKey('');
      setName('');
      setDescription('');
      setKafkaTopic('');
      setCorrelationKeyPath('');
      setPayloadSchema('{\n  "amount": 1000,\n  "cafId": "CAF123"\n}');
      setActive(true);
    }
  }, [event]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!eventKey.trim()) {
      onShowNotification('event key is required.', 'error');
      return;
    }

    // Validate payloadSchema JSON
    if (payloadSchema.trim()) {
      try {
        JSON.parse(payloadSchema);
      } catch (err) {
        onShowNotification('payload schema must be a valid json object.', 'error');
        return;
      }
    }

    setSubmitting(true);
    try {
      const payload = {
        eventKey: eventKey.trim().toUpperCase().replace(/\s+/g, '_'),
        name: name.trim() || eventKey.trim(),
        description: description.trim(),
        kafkaTopic: kafkaTopic.trim(),
        correlationKeyPath: correlationKeyPath.trim(),
        payloadSchema: payloadSchema.trim(),
        active
      };

      const url = event ? `/api/event-definitions/${event.id}` : '/api/event-definitions';
      const method = event ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        if (res.status === 409) {
          throw new Error('an event with this event key already exists.');
        }
        throw new Error('failed to save event definition');
      }

      onShowNotification(event ? 'event updated successfully!' : 'event registered successfully!', 'success');
      await onRefresh();
      onClose();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const accentColor = '#8b5cf6'; // Violet theme color

  return (
    <Container maxWidth="md" sx={{ py: 2 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
        <IconButton onClick={onClose} sx={{ color: accentColor, border: `1px solid rgba(139,92,246,0.2)` }}>
          <ArrowBackIcon />
        </IconButton>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
            {event ? 'modify event definition' : 'register new event'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            configure broker topics and correlation identifier extraction rules.
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
          border: `1px solid rgba(139,92,246,0.15)`,
          borderRadius: 3,
          boxShadow: '0 8px 32px rgba(0,0,0,0.1)',
          display: 'flex',
          flexDirection: 'column',
          gap: 3
        }}
      >
        {/* Active Status */}
        <FormControlLabel
          control={
            <Switch checked={active} onChange={(e) => setActive(e.target.checked)} color="primary" />
          }
          label={active ? 'event listening active' : 'event listening suspended'}
          sx={{ '& .MuiFormControlLabel-label': { fontWeight: 700 } }}
        />

        {/* Event Key */}
        <TextField
          fullWidth size="small" label="Event Key (Unique)"
          placeholder="PAYMENT_RECEIVED"
          value={eventKey}
          onChange={(e) => setEventKey(e.target.value)}
          disabled={!!event} // Event Key immutable after creation
          helperText="uppercase unique identifier (e.g. PAYMENT_RECEIVED, OTP_VERIFIED)"
          slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Name */}
        <TextField
          fullWidth size="small" label="Event Name"
          placeholder="Payment Received Event"
          value={name}
          onChange={(e) => setName(e.target.value)}
          slotProps={{ input: { sx: { color: 'text.primary' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Description */}
        <TextField
          fullWidth size="small" label="Description"
          placeholder="Triggered when payment is successfully captured from gateway"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          multiline rows={3}
          slotProps={{ input: { sx: { color: 'text.primary' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)' }} />

        {/* Kafka Topic */}
        <TextField
          fullWidth size="small" label="Kafka Topic Name"
          placeholder="payment-events"
          value={kafkaTopic}
          onChange={(e) => setKafkaTopic(e.target.value)}
          helperText="broker queue topic to subscribe to"
          slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Correlation Path */}
        <TextField
          fullWidth size="small" label="Correlation Key Path"
          placeholder="payload.cafId"
          value={correlationKeyPath}
          onChange={(e) => setCorrelationKeyPath(e.target.value)}
          helperText="nested dot notation to extract businessKey from event JSON (e.g. payload.user.id)"
          slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        {/* Payload Schema JSON */}
        <TextField
          fullWidth label="Payload Schema (JSON Template)"
          placeholder={`{\n  "amount": 1000,\n  "cafId": "CAF123"\n}`}
          value={payloadSchema}
          onChange={(e) => setPayloadSchema(e.target.value)}
          multiline rows={8}
          helperText="standard payload structure to populate when simulating/testing this event"
          slotProps={{ input: { sx: { color: 'text.primary', fontFamily: 'monospace', fontSize: '12px' } } }}
          sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
        />

        <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', my: 1 }} />

        {/* Actions */}
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
              background: `linear-gradient(135deg, ${accentColor} 0%, #6d28d9 100%)`,
              boxShadow: `0 4px 12px rgba(139,92,246,0.3)`,
              fontWeight: 700,
              textTransform: 'lowercase',
              borderRadius: 2,
              px: 4,
              '&:hover': { background: '#6d28d9' }
            }}
          >
            {submitting ? 'saving...' : 'save event definition'}
          </Button>
        </Box>
      </Paper>
    </Container>
  );
};
