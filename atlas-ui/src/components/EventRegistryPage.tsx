import React, { useEffect, useState } from 'react';
import {
  Box, Container, Grid, Paper, Typography, Button, Card, CardContent,
  CardActions, Chip, IconButton, InputBase, CircularProgress, Divider,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import CodeIcon from '@mui/icons-material/Code';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
import ToggleOffIcon from '@mui/icons-material/ToggleOff';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import TopicIcon from '@mui/icons-material/Topic';
import { useWorkflowStore } from '../store/workflowStore';
import type { EventDefinition } from '../store/workflowStore';
import { EventForm } from './EventForm';

interface EventRegistryPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const EventRegistryPage: React.FC<EventRegistryPageProps> = ({ onShowNotification }) => {
  const { goBack } = useWorkflowStore();
  const [events, setEvents] = useState<EventDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'create' | 'edit'>('list');
  const [selectedEvent, setSelectedEvent] = useState<EventDefinition | null>(null);

  // Simulation state
  const [simulationOpen, setSimulationOpen] = useState(false);
  const [simulationEvent, setSimulationEvent] = useState<EventDefinition | null>(null);
  const [simulationPayload, setSimulationPayload] = useState('');
  const [simulating, setSimulating] = useState(false);

  const fetchEvents = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/event-definitions');
      if (!res.ok) throw new Error('Failed to fetch event definitions');
      const data = await res.json();
      setEvents(data);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  const handleDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this event definition? Worflows listening on this event key will no longer resume.')) return;
    try {
      const res = await fetch(`/api/event-definitions/${id}`, { method: 'DELETE' });
      if (!res.ok) throw new Error('Failed to delete event definition');
      onShowNotification('Event definition deleted from registry', 'success');
      await fetchEvents();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    }
  };

  const handleEdit = (event: EventDefinition) => {
    setSelectedEvent(event);
    setViewMode('edit');
  };

  const handleCreateNew = () => {
    setSelectedEvent(null);
    setViewMode('create');
  };

  const handleOpenSimulation = (event: EventDefinition) => {
    setSimulationEvent(event);
    
    // Generate UUID for simulation
    const mockUuid = 'sim-' + Math.random().toString(36).substring(2, 11) + '-' + Math.random().toString(36).substring(2, 6);
    
    // Parse template payload
    let parsedTemplate = {};
    if (event.payloadSchema) {
      try {
        parsedTemplate = JSON.parse(event.payloadSchema);
      } catch (err) {
        // Fallback to empty if invalid template
      }
    }

    const payloadObj = {
      eventId: mockUuid,
      eventType: event.eventKey,
      timestamp: new Date().toISOString(),
      businessKey: '',
      payload: parsedTemplate
    };

    setSimulationPayload(JSON.stringify(payloadObj, null, 2));
    setSimulationOpen(true);
  };

  const handleRunSimulation = async () => {
    let parsedBody;
    try {
      parsedBody = JSON.parse(simulationPayload);
    } catch (err) {
      onShowNotification('Simulation payload is not a valid JSON structure.', 'error');
      return;
    }

    setSimulating(true);
    try {
      const res = await fetch('/api/events', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(parsedBody)
      });

      if (!res.ok) throw new Error('Failed to ingest event simulation payload');
      
      onShowNotification('Event successfully ingested and dispatched to routing queue.', 'success');
      setSimulationOpen(false);
      setSimulationEvent(null);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setSimulating(false);
    }
  };

  const filteredEvents = events.filter(e =>
    e.name.toLowerCase().includes(search.toLowerCase()) ||
    e.eventKey.toLowerCase().includes(search.toLowerCase()) ||
    (e.description && e.description.toLowerCase().includes(search.toLowerCase())) ||
    (e.kafkaTopic && e.kafkaTopic.toLowerCase().includes(search.toLowerCase()))
  );

  const accentColor = '#8b5cf6'; // Violet theme accent

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Title bar */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <IconButton onClick={() => goBack()} sx={{ color: accentColor, border: `1px solid rgba(139,92,246,0.2)` }}>
              <ArrowBackIcon />
            </IconButton>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
                Predefined Event Registry
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Configure broker topics and correlation key path mapping for async transactions.
              </Typography>
            </Box>
          </Box>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button
              variant="contained" startIcon={<AddIcon />}
              onClick={handleCreateNew}
              sx={{
                background: `linear-gradient(135deg, ${accentColor} 0%, #6d28d9 100%)`,
                boxShadow: `0 4px 14px rgba(139,92,246,0.3)`,
                fontWeight: 700,
                textTransform: 'lowercase', // CTA lower-case constraint
                borderRadius: 2,
                '&:hover': { background: '#6d28d9' }
              }}
            >
              register event
            </Button>
          </Box>
        </Box>

        {viewMode !== 'list' ? (
          <EventForm
            event={selectedEvent}
            onClose={() => setViewMode('list')}
            onRefresh={fetchEvents}
            onShowNotification={onShowNotification}
          />
        ) : (
          <>
            {/* Search bar */}
            <Paper sx={{
              p: '4px 12px', display: 'flex', alignItems: 'center', mb: 4,
              bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2
            }}>
              <SearchIcon sx={{ color: 'text.secondary', mr: 1 }} />
              <InputBase
                placeholder="Search events by Key, Name, Description, or Topic..."
                value={search} onChange={(e) => setSearch(e.target.value)}
                sx={{ ml: 1, flex: 1, color: 'text.primary', fontSize: '14px' }}
              />
            </Paper>

            {/* Loading / Content */}
            {loading && events.length === 0 ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
                <CircularProgress color="secondary" />
              </Box>
            ) : filteredEvents.length === 0 ? (
              <Box sx={{
                p: 8, border: '1px dashed', borderColor: 'divider', borderRadius: 2,
                textAlign: 'center', bgcolor: 'background.paper'
              }}>
                <Typography variant="body1" color="text.secondary">
                  No event definitions registered. Click "Register Event" to add one.
                </Typography>
              </Box>
            ) : (
              <Grid container spacing={3}>
                {filteredEvents.map((ev) => {
                  return (
                    <Grid size={{ xs: 12, sm: 6, md: 4 }} key={ev.id}>
                      <Card sx={{
                        height: '100%', display: 'flex', flexDirection: 'column',
                        bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider',
                        borderRadius: 2,
                        transition: 'transform 0.2s, border-color 0.2s',
                        '&:hover': {
                          transform: 'translateY(-2px)',
                          borderColor: accentColor
                        }
                      }}>
                        <CardContent sx={{ flexGrow: 1, p: 2.5 }}>
                          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1.5 }}>
                            <Box sx={{ overflow: 'hidden', mr: 1 }}>
                              <Typography variant="caption" sx={{
                                fontFamily: 'monospace', color: accentColor, fontWeight: 800,
                                letterSpacing: '1px', display: 'block', mb: 0.5, textOverflow: 'ellipsis', overflow: 'hidden'
                              }}>
                                {ev.eventKey}
                              </Typography>
                              <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                                {ev.name}
                              </Typography>
                            </Box>
                            {/* Status Chip */}
                            <Chip
                              label={ev.active ? "ACTIVE" : "INACTIVE"}
                              size="small"
                              sx={{
                                height: 18, fontSize: '8px', fontWeight: 800,
                                bgcolor: ev.active ? 'rgba(34, 197, 94, 0.12)' : 'rgba(239, 68, 68, 0.12)',
                                color: ev.active ? '#22c55e' : '#ef4444',
                                border: ev.active ? '1px solid rgba(34, 197, 94, 0.2)' : '1px solid rgba(239, 68, 68, 0.2)'
                              }}
                            />
                          </Box>

                          <Typography variant="body2" color="text.secondary" sx={{
                            minHeight: 40, mb: 2.5, display: '-webkit-box',
                            WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'
                          }}>
                            {ev.description || 'No description provided.'}
                          </Typography>

                          <Divider sx={{ my: 1.5, borderColor: 'rgba(255,255,255,0.06)' }} />

                          {/* Broker & Path metadata */}
                          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, mb: 2 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <TopicIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                              <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
                                Topic: <span style={{ color: '#8b5cf6', fontWeight: 700 }}>{ev.kafkaTopic || 'N/A'}</span>
                              </Typography>
                            </Box>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <SwapHorizIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                              <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
                                Correlation: <span style={{ color: '#22c55e', fontWeight: 700 }}>{ev.correlationKeyPath || 'N/A'}</span>
                              </Typography>
                            </Box>
                          </Box>

                          {/* Payload Schema Template Preview */}
                          {ev.payloadSchema && (
                            <Box sx={{ bgcolor: 'rgba(0,0,0,0.2)', p: 1.5, borderRadius: 1.5, border: '1px solid rgba(255,255,255,0.04)', mb: 2 }}>
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, mb: 0.8 }}>
                                <CodeIcon sx={{ fontSize: 13, color: 'text.secondary' }} />
                                <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px' }}>
                                  PAYLOAD TEMPLATE
                                </Typography>
                              </Box>
                              <Typography variant="body2" sx={{
                                fontFamily: 'monospace', color: '#a78bfa',
                                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                                fontSize: '11px'
                              }}>
                                {ev.payloadSchema.replace(/\s+/g, ' ')}
                              </Typography>
                            </Box>
                          )}

                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            {ev.active ? (
                              <ToggleOnIcon sx={{ color: '#22c55e', fontSize: 16 }} />
                            ) : (
                              <ToggleOffIcon sx={{ color: '#ef4444', fontSize: 16 }} />
                            )}
                            <Typography variant="caption" color="text.secondary">
                              Listener status: <b>{ev.active ? 'enabled' : 'disabled'}</b>
                            </Typography>
                          </Box>
                        </CardContent>

                        <CardActions sx={{ justifyContent: 'space-between', px: 2.5, pb: 2.5, pt: 0 }}>
                          <Button
                            size="small" variant="outlined" startIcon={<PlayArrowIcon />}
                            onClick={() => handleOpenSimulation(ev)}
                            sx={{
                              borderColor: accentColor,
                              color: accentColor,
                              fontWeight: 700,
                              borderRadius: 2,
                              textTransform: 'lowercase', // CTA lower-case constraint
                              '&:hover': {
                                borderColor: accentColor,
                                bgcolor: 'rgba(139,92,246,0.08)'
                              }
                            }}
                          >
                            simulate trigger
                          </Button>
                          <Box>
                            <IconButton size="small" onClick={() => handleEdit(ev)} sx={{ color: 'text.secondary', '&:hover': { color: accentColor } }}>
                              <EditIcon sx={{ fontSize: 18 }} />
                            </IconButton>
                            <IconButton size="small" color="error" onClick={(e) => handleDelete(ev.id, e)}>
                              <DeleteIcon sx={{ fontSize: 18 }} />
                            </IconButton>
                          </Box>
                        </CardActions>
                      </Card>
                    </Grid>
                  );
                })}
              </Grid>
            )}
          </>
        )}

        {/* Simulation Dialog */}
        <Dialog
          open={simulationOpen}
          onClose={() => setSimulationOpen(false)}
          fullWidth maxWidth="sm"
          slotProps={{ paper: { sx: { bgcolor: 'background.paper', color: 'text.primary', borderRadius: 2 } } }}
        >
          <DialogTitle sx={{ fontWeight: 800 }}>
            Simulate Predefined Event Ingestion
          </DialogTitle>
          <DialogContent>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Simulate an inbound broker payload targeting <b>{simulationEvent?.eventKey}</b>. The correlation routing service will extract the identifier key to resume the matching workflow transaction.
            </Typography>
            <TextField
              fullWidth
              multiline
              rows={12}
              label="Standard Event JSON Payload"
              value={simulationPayload}
              onChange={(e) => setSimulationPayload(e.target.value)}
              slotProps={{ input: { sx: { fontFamily: 'monospace', fontSize: '13px', color: 'text.primary' } } }}
              sx={{ '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' } }}
            />
          </DialogContent>
          <DialogActions sx={{ p: 2.5, gap: 1 }}>
            <Button
              onClick={() => setSimulationOpen(false)}
              color="inherit"
              sx={{ textTransform: 'lowercase', fontWeight: 700 }}
            >
              cancel
            </Button>
            <Button
              onClick={handleRunSimulation}
              variant="contained"
              disabled={simulating}
              startIcon={simulating ? <CircularProgress size={14} color="inherit" /> : <PlayArrowIcon />}
              sx={{
                background: `linear-gradient(135deg, ${accentColor} 0%, #6d28d9 100%)`,
                boxShadow: `0 4px 14px rgba(139,92,246,0.3)`,
                fontWeight: 700,
                textTransform: 'lowercase', // CTA lower-case constraint
                borderRadius: 2
              }}
            >
              {simulating ? 'ingesting...' : 'ingest simulation event'}
            </Button>
          </DialogActions>
        </Dialog>
      </Container>
    </Box>
  );
};
