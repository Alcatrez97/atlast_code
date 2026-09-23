import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Tabs,
  Tab,
  TextField,
  Alert,
  AlertTitle,
  Checkbox,
  FormControlLabel,
  CircularProgress,
  IconButton,
  Chip,
  Paper,
  Tooltip,
  Divider
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import TerminalIcon from '@mui/icons-material/Terminal';
import SendIcon from '@mui/icons-material/Send';
import AssignmentIcon from '@mui/icons-material/Assignment';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';

export const ResumeInstructionsDialog = ({ open, onClose, instance, onShowNotification, onSuccess }) => {
  const [activeTab, setActiveTab] = useState(0);
  const [subscriptions, setSubscriptions] = useState([]);
  const [revertStatusList, setRevertStatusList] = useState([]);
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [additionalContextJson, setAdditionalContextJson] = useState('{\n  \n}');
  const [confirmedSafety, setConfirmedSafety] = useState(false);
  const [resuming, setResuming] = useState(false);
  const [copiedKey, setCopiedKey] = useState(null);

  useEffect(() => {
    if (open && instance?.id) {
      setConfirmedSafety(false);
      setAdditionalContextJson('{\n  \n}');
      setActiveTab(0);
      fetchMetadata(instance.id);
    }
  }, [open, instance?.id]);

  const fetchMetadata = async (instId) => {
    setLoadingMeta(true);
    try {
      const [subsRes, revertRes] = await Promise.all([
        fetch(`/api/instances/${instId}/subscriptions`),
        fetch(`/api/instances/${instId}/revert-status`)
      ]);

      if (subsRes.ok) {
        const subsData = await subsRes.json();
        setSubscriptions(subsData || []);
      }
      if (revertRes.ok) {
        const revertData = await revertRes.json();
        setRevertStatusList(revertData || []);
      }
    } catch (err) {
      console.warn('Failed to load instance resume metadata:', err);
    } finally {
      setLoadingMeta(false);
    }
  };

  const handleCopy = (text, key) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2500);
  };

  const handleResumeSubmit = async () => {
    if (!instance?.id) return;
    let payload = {};
    try {
      payload = JSON.parse(additionalContextJson);
    } catch (e) {
      onShowNotification?.('Invalid additional context JSON format', 'error');
      return;
    }

    setResuming(true);
    try {
      const res = await fetch(`/api/instances/${instance.id}/resume`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || 'Failed to resume workflow instance');
      }

      onShowNotification?.('Workflow instance resumed successfully via Operator Override', 'success');
      onSuccess?.();
      onClose();
    } catch (err) {
      onShowNotification?.(err.message, 'error');
    } finally {
      setResuming(false);
    }
  };

  if (!instance) return null;

  // Derive correlation keys & event targets
  const businessKey = instance.businessKey || instance.context?.contextId || instance.context?.cafId || instance.context?.cocpId || instance.id;
  const activeSub = subscriptions.find(s => s.status === 'ACTIVE') || subscriptions[0];
  const eventType = activeSub?.eventType || (instance.currentNodeId ? `${instance.currentNodeId.toUpperCase()}_RESUME` : 'WORKFLOW_RESUME');
  const pendingBucket = revertStatusList.find(r => r.status === 'PENDING') || revertStatusList[0];

  // Code snippets
  const kafkaPayload = JSON.stringify({
    eventType: eventType,
    businessKey: businessKey,
    payload: {
      status: "APPROVED",
      resumedBy: "ManualOperator",
      notes: "Resumed via external event pattern"
    }
  }, null, 2);

  const kafkaCommand = `# Publish event to Kafka:\nwsl kafka-console-producer.sh --bootstrap-server localhost:9092 --topic workflow-events <<EOF\n${kafkaPayload}\nEOF`;

  const curlCommand = `curl -X POST "http://localhost:9091/api/instances/${instance.id}/resume" \\\n  -H "Content-Type: application/json" \\\n  -d '{\n    "status": "APPROVED",\n    "resumedBy": "ManualOperator"\n  }'`;

  const bucketCurl = pendingBucket ? `curl -X PUT "http://localhost:9091/api/forms/${businessKey}/status" \\\n  -H "Content-Type: application/json" \\\n  -d '{\n    "status": "${pendingBucket.bucketId}Accept",\n    "outcome": "Accept",\n    "notes": "Approved via manual operator review"\n  }'` : '';

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="md"
      slotProps={{
        paper: {
          sx: {
            bgcolor: 'background.paper',
            color: 'text.primary',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 3
          }
        }
      }}
    >
      <DialogTitle sx={{ pb: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              How to Resume Instance
            </Typography>
            <Chip label="WAITING" size="small" color="warning" sx={{ fontWeight: 700, height: 22, fontSize: '11px' }} />
          </Box>
          <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
            Instance ID: {instance.id} | Workflow: {instance.workflowKey}
          </Typography>
        </Box>
        <IconButton size="small" onClick={onClose} sx={{ color: 'text.secondary' }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ pt: 1 }}>
        {/* Mitigation Alert & Operator Guard */}
        <Alert
          severity="warning"
          icon={<WarningAmberIcon fontSize="inherit" />}
          sx={{
            mb: 2.5,
            borderRadius: 2,
            border: '1px solid rgba(245, 158, 11, 0.3)',
            bgcolor: 'rgba(245, 158, 11, 0.08)'
          }}
        >
          <AlertTitle sx={{ fontWeight: 800, fontSize: '13px', mb: 0.5 }}>
            Safety Mitigation & Operator Guard
          </AlertTitle>
          <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.5 }}>
            Resuming an instance manually bypasses external webhooks, Kafka event delivery, and third-party validation.
            Review downstream node requirements and ensure you have proper authorization before executing a direct override.
          </Typography>
        </Alert>

        {/* Current State Diagnostic Tile */}
        <Paper
          variant="outlined"
          sx={{
            p: 1.5,
            mb: 2.5,
            borderRadius: 2,
            bgcolor: 'rgba(255, 255, 255, 0.02)',
            display: 'flex',
            flexWrap: 'wrap',
            gap: 3,
            fontSize: '12px'
          }}
        >
          <Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontWeight: 600 }}>
              WAITING AT NODE
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700, color: '#f59e0b', fontFamily: 'monospace' }}>
              {instance.currentNodeLabel || instance.currentNodeId || 'Unknown'}
            </Typography>
          </Box>
          <Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontWeight: 600 }}>
              CORRELATION KEY (BUSINESS KEY)
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
              {businessKey}
            </Typography>
          </Box>
          {activeSub && (
            <Box>
              <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontWeight: 600 }}>
                ACTIVE EVENT SUBSCRIPTION
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 700, color: '#10b981', fontFamily: 'monospace' }}>
                {activeSub.eventType}
              </Typography>
            </Box>
          )}
          {pendingBucket && (
            <Box>
              <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontWeight: 600 }}>
                PENDING BUCKET
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 700, color: '#6366f1' }}>
                {pendingBucket.bucketName || pendingBucket.bucketId} ({pendingBucket.bucketId})
              </Typography>
            </Box>
          )}
        </Paper>

        {/* Navigation Tabs */}
        <Tabs
          value={activeTab}
          onChange={(_, v) => setActiveTab(v)}
          sx={{
            borderBottom: 1,
            borderColor: 'divider',
            mb: 2,
            '& .MuiTab-root': { textTransform: 'none', fontWeight: 700, fontSize: '12px' }
          }}
        >
          <Tab icon={<PlayArrowIcon sx={{ fontSize: 16 }} />} iconPosition="start" label="Direct UI Resume" />
          <Tab icon={<SendIcon sx={{ fontSize: 16 }} />} iconPosition="start" label="Kafka Inbound Event" />
          <Tab icon={<TerminalIcon sx={{ fontSize: 16 }} />} iconPosition="start" label="cURL Command" />
          {pendingBucket && (
            <Tab icon={<AssignmentIcon sx={{ fontSize: 16 }} />} iconPosition="start" label="Bucket Form Update" />
          )}
        </Tabs>

        {/* Tab 0: Direct UI Resume */}
        {activeTab === 0 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              Directly resume this workflow instance using the Engine's Execution Service. Optionally inject variables into the context:
            </Typography>

            <TextField
              multiline
              rows={6}
              fullWidth
              value={additionalContextJson}
              onChange={(e) => setAdditionalContextJson(e.target.value)}
              placeholder="{\n  &quot;approved&quot;: true\n}"
              sx={{
                '& .MuiOutlinedInput-root': {
                  fontFamily: 'monospace',
                  fontSize: '12px',
                  bgcolor: 'rgba(0, 0, 0, 0.04)'
                }
              }}
            />

            <FormControlLabel
              control={
                <Checkbox
                  checked={confirmedSafety}
                  onChange={(e) => setConfirmedSafety(e.target.checked)}
                  color="warning"
                />
              }
              label={
                <Typography variant="caption" sx={{ fontWeight: 700, color: confirmedSafety ? 'text.primary' : 'warning.main' }}>
                  I confirm that this manual override is authorized and the payload context is verified.
                </Typography>
              }
            />
          </Box>
        )}

        {/* Tab 1: Kafka Event */}
        {activeTab === 1 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              <strong>Standard Event-Driven Integration Pattern:</strong> Downstream microservices, message queues, or integration partners can resume this instance by emitting a matching event to Kafka.
            </Typography>

            <Paper
              sx={{
                p: 2,
                bgcolor: 'rgba(0, 0, 0, 0.05)',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                position: 'relative'
              }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                  KAFKA EVENT PAYLOAD (JSON)
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'kafka' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(kafkaPayload, 'kafka')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'kafka' ? 'Copied!' : 'Copy JSON'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto' }}>
                {kafkaPayload}
              </pre>
            </Paper>

            <Paper
              sx={{
                p: 2,
                bgcolor: 'rgba(0, 0, 0, 0.05)',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                position: 'relative'
              }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                  KAFKA CONSOLE PRODUCER CLI
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'kafkaCli' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(kafkaCommand, 'kafkaCli')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'kafkaCli' ? 'Copied!' : 'Copy CLI'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>
                {kafkaCommand}
              </pre>
            </Paper>
          </Box>
        )}

        {/* Tab 2: cURL Command */}
        {activeTab === 2 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              <strong>Direct REST Engine Endpoint:</strong> SREs or automated remediation scripts can execute this curl command from any terminal or CI/CD pipeline:
            </Typography>

            <Paper
              sx={{
                p: 2,
                bgcolor: 'rgba(0, 0, 0, 0.05)',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                position: 'relative'
              }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                  HTTP POST CURL COMMAND
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'curl' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(curlCommand, 'curl')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'curl' ? 'Copied!' : 'Copy cURL'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>
                {curlCommand}
              </pre>
            </Paper>
          </Box>
        )}

        {/* Tab 3: Bucket Form Update */}
        {activeTab === 3 && pendingBucket && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              <strong>Human-In-The-Loop Bucket Resolution:</strong> This instance is suspended on business outcome bucket <strong>{pendingBucket.bucketName || pendingBucket.bucketId}</strong>. Resolving the domain entity in the database will resume the workflow.
            </Typography>

            <Paper
              sx={{
                p: 2,
                bgcolor: 'rgba(0, 0, 0, 0.05)',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                position: 'relative'
              }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                  SIMULATOR STATUS UPDATE (PUT /api/forms/:id/status)
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'bucketCurl' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(bucketCurl, 'bucketCurl')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'bucketCurl' ? 'Copied!' : 'Copy cURL'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>
                {bucketCurl}
              </pre>
            </Paper>
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5, pt: 1, display: 'flex', justifyContent: 'space-between' }}>
        <Button onClick={onClose} sx={{ color: 'text.secondary' }}>
          Close
        </Button>

        {activeTab === 0 && (
          <Button
            variant="contained"
            color="warning"
            onClick={handleResumeSubmit}
            disabled={!confirmedSafety || resuming}
            startIcon={resuming ? <CircularProgress size={16} color="inherit" /> : <PlayArrowIcon />}
            sx={{ fontWeight: 700, px: 3 }}
          >
            {resuming ? 'Resuming...' : 'Confirm & Execute Resume'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};
