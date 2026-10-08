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
  Divider,
  Collapse
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import TerminalIcon from '@mui/icons-material/Terminal';
import SendIcon from '@mui/icons-material/Send';
import AssignmentIcon from '@mui/icons-material/Assignment';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';

export const ResumeInstructionsDialog = ({ open, onClose, instance, onShowNotification, onSuccess }) => {
  const [activeTab, setActiveTab] = useState(0);
  const [subscriptions, setSubscriptions] = useState([]);
  const [revertStatusList, setRevertStatusList] = useState([]);
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [additionalContextJson, setAdditionalContextJson] = useState('{\n  \n}');
  const [confirmedSafety, setConfirmedSafety] = useState(false);
  const [resuming, setResuming] = useState(false);
  const [copiedKey, setCopiedKey] = useState(null);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [resolvedBy, setResolvedBy] = useState('ManualOperator');
  const [showAdvancedJson, setShowAdvancedJson] = useState(false);

  useEffect(() => {
    if (open && instance?.id) {
      setConfirmedSafety(false);
      setAdditionalContextJson('{\n  \n}');
      setResolutionNotes('');
      setResolvedBy('ManualOperator');
      setShowAdvancedJson(false);
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

  if (!instance) return null;

  // Derive correlation keys & bucket targets
  const cafId = instance.businessKey || instance.context?.cafId || instance.context?.contextId || instance.context?.cocpId || instance.id;
  const activeSub = subscriptions.find(s => s.status === 'ACTIVE') || subscriptions[0];
  const pendingBucket = revertStatusList.find(r => r.status === 'PENDING') || revertStatusList[0];
  
  const isBucketNode = Boolean(
    pendingBucket ||
    instance.currentNodeId?.toLowerCase().includes('bucket') ||
    instance.currentNodeLabel?.toLowerCase().includes('bucket')
  );

  const bucketId = pendingBucket?.bucketId || 
    (instance.currentNodeId ? instance.currentNodeId.replace(/^(node[-_]|bucket[-_])/i, '') : 'A2');
  const bucketName = pendingBucket?.bucketName || instance.currentNodeLabel || `Bucket ${bucketId}`;
  const eventType = activeSub?.eventType || bucketId;

  // Quick Resolve Bucket Action (Accept or Reject)
  const handleResolveBucketSubmit = async (outcome) => {
    if (!instance?.id) return;
    setResuming(true);
    try {
      // 1. Try updating via Domain Form API using cafId
      const targetStatus = `${bucketId}${outcome}`;
      const res = await fetch(`/api/forms/${cafId}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: targetStatus,
          outcome: outcome,
          resolvedBy: resolvedBy || 'ManualOperator',
          notes: resolutionNotes || `Bucket ${bucketId} marked as ${outcome} via Atlas UI Operator`
        })
      });

      if (!res.ok) {
        // 2. Fallback to direct instance resume if form record does not exist
        const fallbackRes = await fetch(`/api/instances/${instance.id}/resume`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            outcome: outcome,
            lastOutcome: outcome,
            lastBucketId: bucketId,
            form_status: targetStatus,
            [`${bucketId}_outcome`]: outcome,
            resolvedBy: resolvedBy || 'ManualOperator',
            notes: resolutionNotes || `Bucket ${bucketId} resolved via UI fallback`
          })
        });

        if (!fallbackRes.ok) {
          const errText = await fallbackRes.text();
          throw new Error(errText || 'Failed to resolve bucket and resume instance');
        }
      }

      onShowNotification?.(`Bucket "${bucketName}" resolved as ${outcome.toUpperCase()}! Workflow resumed.`, 'success');
      onSuccess?.();
      onClose();
    } catch (err) {
      onShowNotification?.(err.message, 'error');
    } finally {
      setResuming(false);
    }
  };

  // Generic direct resume submit
  const handleDirectResumeSubmit = async () => {
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

  // Code snippets for external resolution
  const restAcceptCurl = `curl -X PUT "http://localhost:9091/api/forms/${cafId}/status" \\
  -H "Content-Type: application/json" \\
  -d '{
    "status": "${bucketId}Accept",
    "outcome": "Accept",
    "resolvedBy": "external-approval-portal",
    "notes": "Verified by external approval system"
  }'`;

  const restRejectCurl = `curl -X PUT "http://localhost:9091/api/forms/${cafId}/status" \\
  -H "Content-Type: application/json" \\
  -d '{
    "status": "${bucketId}Reject",
    "outcome": "Reject",
    "resolvedBy": "external-approval-portal",
    "notes": "Rejected by external approval system"
  }'`;

  const kafkaResolutionPayload = JSON.stringify({
    cafId: cafId,
    bucketId: bucketId,
    outcome: "Accept",
    resolvedBy: "external-approval-service",
    resolutionNotes: "Identity and documents approved"
  }, null, 2);

  const kafkaResolutionCli = `# Send resolution using only cafId & bucketId (no instanceId needed!):
wsl kafka-console-producer.sh --bootstrap-server localhost:9092 --topic workflow-bucket-resolution <<EOF
${kafkaResolutionPayload}
EOF`;

  const directEngineCurl = `curl -X POST "http://localhost:9091/api/instances/${instance.id}/resume" \\
  -H "Content-Type: application/json" \\
  -d '{
    "outcome": "Accept",
    "lastOutcome": "Accept",
    "form_status": "${bucketId}Accept",
    "resolvedBy": "Operator"
  }'`;

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
              {isBucketNode ? `Resolve Bucket & Resume Workflow` : `How to Resume Instance`}
            </Typography>
            <Chip label="WAITING" size="small" color="warning" sx={{ fontWeight: 700, height: 22, fontSize: '11px' }} />
            {isBucketNode && (
              <Chip
                label={`Bucket: ${bucketId}`}
                size="small"
                sx={{
                  fontWeight: 700,
                  height: 22,
                  fontSize: '11px',
                  bgcolor: 'rgba(99,102,241,0.15)',
                  color: '#818cf8',
                  border: '1px solid rgba(99,102,241,0.3)'
                }}
              />
            )}
          </Box>
          <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
            Workflow: <strong>{instance.workflowKey}</strong> | Instance: {instance.id}
          </Typography>
        </Box>
        <IconButton size="small" onClick={onClose} sx={{ color: 'text.secondary' }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ pt: 1 }}>
        {/* State Diagnostic Tile */}
        <Paper
          variant="outlined"
          sx={{
            p: 2,
            mb: 2.5,
            borderRadius: 2,
            bgcolor: 'rgba(255, 255, 255, 0.02)',
            display: 'flex',
            flexWrap: 'wrap',
            justifyContent: 'space-between',
            gap: 2,
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
              CAF ID / BUSINESS KEY
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <Typography variant="body2" sx={{ fontWeight: 800, color: '#38bdf8', fontFamily: 'monospace' }}>
                {cafId}
              </Typography>
              <Tooltip title="Copy CAF ID">
                <IconButton size="small" onClick={() => handleCopy(cafId, 'cafIdCopy')} sx={{ p: 0.25 }}>
                  {copiedKey === 'cafIdCopy' ? <CheckIcon sx={{ fontSize: 14, color: '#34d399' }} /> : <ContentCopyIcon sx={{ fontSize: 14 }} />}
                </IconButton>
              </Tooltip>
            </Box>
          </Box>

          <Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontWeight: 600 }}>
              BUCKET KEY
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700, color: '#a78bfa', fontFamily: 'monospace' }}>
              {bucketId}
            </Typography>
          </Box>

          <Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontWeight: 600 }}>
              EVENT CORRELATION
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700, color: '#34d399', fontFamily: 'monospace' }}>
              {eventType}
            </Typography>
          </Box>
        </Paper>

        {/* Navigation Tabs */}
        <Tabs
          value={activeTab}
          onChange={(_, v) => setActiveTab(v)}
          sx={{
            borderBottom: 1,
            borderColor: 'divider',
            mb: 2.5,
            '& .MuiTab-root': { textTransform: 'none', fontWeight: 700, fontSize: '12px' }
          }}
        >
          <Tab icon={<PlayArrowIcon sx={{ fontSize: 16 }} />} iconPosition="start" label="One-Click UI Action" />
          <Tab icon={<TerminalIcon sx={{ fontSize: 16 }} />} iconPosition="start" label="External REST API (cafId)" />
          <Tab icon={<SendIcon sx={{ fontSize: 16 }} />} iconPosition="start" label="Kafka Resolution Topic" />
        </Tabs>

        {/* Tab 0: Direct UI Action */}
        {activeTab === 0 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            {isBucketNode ? (
              <>
                <Alert
                  severity="info"
                  sx={{
                    borderRadius: 2,
                    bgcolor: 'rgba(56, 189, 248, 0.08)',
                    border: '1px solid rgba(56, 189, 248, 0.25)'
                  }}
                >
                  <AlertTitle sx={{ fontWeight: 800, fontSize: '13px' }}>
                    Human-in-the-Loop Bucket Action Required
                  </AlertTitle>
                  <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.5 }}>
                    This workflow is waiting on bucket <strong>{bucketName} ({bucketId})</strong>. Clicking <strong>Accept</strong> or <strong>Reject</strong> below will immediately update the database form record (<code>POSTPAID_ONBOARD_CAF</code>), complete the audit trail, and resume workflow routing.
                  </Typography>
                </Alert>

                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  <TextField
                    size="small"
                    label="Resolved By"
                    value={resolvedBy}
                    onChange={(e) => setResolvedBy(e.target.value)}
                    sx={{ flex: '1 1 200px' }}
                  />
                  <TextField
                    size="small"
                    label="Resolution Notes / Remarks"
                    placeholder="e.g. Identity and address verification passed"
                    value={resolutionNotes}
                    onChange={(e) => setResolutionNotes(e.target.value)}
                    sx={{ flex: '2 1 300px' }}
                  />
                </Box>

                <Box sx={{ display: 'flex', gap: 2, pt: 1 }}>
                  <Button
                    variant="contained"
                    color="success"
                    size="large"
                    disabled={resuming}
                    startIcon={resuming ? <CircularProgress size={16} color="inherit" /> : <CheckCircleIcon />}
                    onClick={() => handleResolveBucketSubmit('Accept')}
                    sx={{
                      flex: 1,
                      fontWeight: 800,
                      py: 1.25,
                      background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                      boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)'
                    }}
                  >
                    Accept Outcome
                  </Button>

                  <Button
                    variant="contained"
                    color="error"
                    size="large"
                    disabled={resuming}
                    startIcon={resuming ? <CircularProgress size={16} color="inherit" /> : <CancelIcon />}
                    onClick={() => handleResolveBucketSubmit('Reject')}
                    sx={{
                      flex: 1,
                      fontWeight: 800,
                      py: 1.25,
                      background: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
                      boxShadow: '0 4px 12px rgba(239, 68, 68, 0.3)'
                    }}
                  >
                    Reject Outcome
                  </Button>
                </Box>

                {/* Collapsible Advanced JSON Section */}
                <Box sx={{ mt: 1 }}>
                  <Button
                    size="small"
                    onClick={() => setShowAdvancedJson(!showAdvancedJson)}
                    endIcon={showAdvancedJson ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                    sx={{ textTransform: 'none', color: 'text.secondary', fontSize: '11px' }}
                  >
                    {showAdvancedJson ? 'Hide Advanced Context Injection' : 'Show Advanced Context Injection'}
                  </Button>

                  <Collapse in={showAdvancedJson}>
                    <Box sx={{ mt: 1.5, p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
                      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
                        Directly inject arbitrary JSON variables into the instance context:
                      </Typography>
                      <TextField
                        multiline
                        rows={4}
                        fullWidth
                        value={additionalContextJson}
                        onChange={(e) => setAdditionalContextJson(e.target.value)}
                        placeholder="{\n  &quot;manualOverride&quot;: true\n}"
                        sx={{
                          '& .MuiOutlinedInput-root': {
                            fontFamily: 'monospace',
                            fontSize: '12px',
                            bgcolor: 'rgba(0, 0, 0, 0.04)'
                          }
                        }}
                      />
                      <FormControlLabel
                        sx={{ mt: 1 }}
                        control={
                          <Checkbox
                            checked={confirmedSafety}
                            onChange={(e) => setConfirmedSafety(e.target.checked)}
                            color="warning"
                          />
                        }
                        label={
                          <Typography variant="caption" sx={{ fontWeight: 700 }}>
                            Confirm direct engine context override
                          </Typography>
                        }
                      />
                      <Box sx={{ mt: 1, textAlign: 'right' }}>
                        <Button
                          variant="outlined"
                          size="small"
                          color="warning"
                          disabled={!confirmedSafety || resuming}
                          onClick={handleDirectResumeSubmit}
                        >
                          Execute Direct Context Override
                        </Button>
                      </Box>
                    </Box>
                  </Collapse>
                </Box>
              </>
            ) : (
              <>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  Directly resume this workflow instance. Inject variables into the context:
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
                      I confirm that this manual override is authorized.
                    </Typography>
                  }
                />
                <Box sx={{ textAlign: 'right' }}>
                  <Button
                    variant="contained"
                    color="warning"
                    onClick={handleDirectResumeSubmit}
                    disabled={!confirmedSafety || resuming}
                    startIcon={resuming ? <CircularProgress size={16} color="inherit" /> : <PlayArrowIcon />}
                    sx={{ fontWeight: 700, px: 3 }}
                  >
                    Confirm & Execute Resume
                  </Button>
                </Box>
              </>
            )}
          </Box>
        )}

        {/* Tab 1: External REST API */}
        {activeTab === 1 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Alert severity="success" sx={{ borderRadius: 2, bgcolor: 'rgba(16, 185, 129, 0.08)', border: '1px solid rgba(16, 185, 129, 0.25)' }}>
              <AlertTitle sx={{ fontWeight: 800, fontSize: '13px' }}>
                External System Integration Hint: No Instance UUID Required
              </AlertTitle>
              <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.5 }}>
                Your external approval portal or CRM does <strong>not</strong> need to store the internal instance UUID. It only needs the business document key (<strong><code>cafId = {cafId}</code></strong>).
              </Typography>
            </Alert>

            {/* Accept cURL */}
            <Paper sx={{ p: 2, bgcolor: 'rgba(0, 0, 0, 0.05)', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 800, color: '#34d399' }}>
                  1. EXTERNAL ACCEPT via PUT /api/forms/{'{cafId}'}/status (RECOMMENDED)
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'acceptCurl' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(restAcceptCurl, 'acceptCurl')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'acceptCurl' ? 'Copied!' : 'Copy cURL'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>
                {restAcceptCurl}
              </pre>
            </Paper>

            {/* Reject cURL */}
            <Paper sx={{ p: 2, bgcolor: 'rgba(0, 0, 0, 0.05)', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 800, color: '#f87171' }}>
                  2. EXTERNAL REJECT via PUT /api/forms/{'{cafId}'}/status
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'rejectCurl' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(restRejectCurl, 'rejectCurl')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'rejectCurl' ? 'Copied!' : 'Copy cURL'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>
                {restRejectCurl}
              </pre>
            </Paper>

            {/* Direct Instance Resume cURL */}
            <Paper sx={{ p: 2, bgcolor: 'rgba(0, 0, 0, 0.05)', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                  3. DIRECT ENGINE OVERRIDE via POST /api/instances/{'{id}'}/resume
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'directCurl' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(directEngineCurl, 'directCurl')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'directCurl' ? 'Copied!' : 'Copy cURL'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>
                {directEngineCurl}
              </pre>
            </Paper>
          </Box>
        )}

        {/* Tab 2: Kafka Resolution */}
        {activeTab === 2 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Alert severity="info" sx={{ borderRadius: 2, bgcolor: 'rgba(99, 102, 241, 0.08)', border: '1px solid rgba(99, 102, 241, 0.25)' }}>
              <AlertTitle sx={{ fontWeight: 800, fontSize: '13px' }}>
                Dedicated Kafka Resolution Topic: workflow-bucket-resolution
              </AlertTitle>
              <Typography variant="caption" sx={{ display: 'block', lineHeight: 1.5 }}>
                External microservices and approval systems can publish directly using <strong><code>cafId</code></strong>. The engine automatically correlates the active workflow instance, updates the database, and advances execution.
              </Typography>
            </Alert>

            <Paper sx={{ p: 2, bgcolor: 'rgba(0, 0, 0, 0.05)', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 800, color: '#818cf8' }}>
                  KAFKA EVENT PAYLOAD (JSON)
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'kafkaJson' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(kafkaResolutionPayload, 'kafkaJson')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'kafkaJson' ? 'Copied!' : 'Copy JSON'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto' }}>
                {kafkaResolutionPayload}
              </pre>
            </Paper>

            <Paper sx={{ p: 2, bgcolor: 'rgba(0, 0, 0, 0.05)', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{ fontWeight: 800, color: '#818cf8' }}>
                  KAFKA CONSOLE PRODUCER CLI COMMAND
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copiedKey === 'kafkaCli' ? <CheckIcon color="success" /> : <ContentCopyIcon />}
                  onClick={() => handleCopy(kafkaResolutionCli, 'kafkaCli')}
                  sx={{ textTransform: 'none', fontSize: '11px', py: 0.25 }}
                >
                  {copiedKey === 'kafkaCli' ? 'Copied!' : 'Copy CLI'}
                </Button>
              </Box>
              <pre style={{ margin: 0, fontSize: '11px', fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>
                {kafkaResolutionCli}
              </pre>
            </Paper>
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5, pt: 1, display: 'flex', justifyContent: 'space-between' }}>
        <Button onClick={onClose} sx={{ color: 'text.secondary' }}>
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};
