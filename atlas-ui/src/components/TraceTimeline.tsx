import React from 'react';
import {
  Box, Typography, Tooltip
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CancelIcon from '@mui/icons-material/Cancel';
import RuleIcon from '@mui/icons-material/Rule';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import HelpIcon from '@mui/icons-material/Help';
import AccessAlarmIcon from '@mui/icons-material/AccessAlarm';
import CallSplitIcon from '@mui/icons-material/CallSplit';
import CallMergeIcon from '@mui/icons-material/CallMerge';
import type { StepRecord } from '../store/workflowStore';

const NODE_TYPE_META: Record<string, { color: string; icon: React.ReactNode }> = {
  START: { color: '#10b981', icon: <PlayArrowIcon sx={{ fontSize: 12 }} /> },
  END: { color: '#ef4444', icon: <CancelIcon sx={{ fontSize: 12 }} /> },
  RULE: { color: '#6366f1', icon: <RuleIcon sx={{ fontSize: 12 }} /> },
  DECISION: { color: '#f59e0b', icon: <HelpIcon sx={{ fontSize: 12 }} /> },
  BUCKET: { color: '#a855f7', icon: <ShoppingBagIcon sx={{ fontSize: 12 }} /> },
  TIMER: { color: '#14b8a6', icon: <AccessAlarmIcon sx={{ fontSize: 12 }} /> },
  PARALLEL: { color: '#f97316', icon: <CallSplitIcon sx={{ fontSize: 12 }} /> },
  JOIN: { color: '#f97316', icon: <CallMergeIcon sx={{ fontSize: 12 }} /> },
};

const STEP_STATUS_COLORS: Record<string, string> = {
  ENTERED: '#6366f1',
  EVALUATED: '#f59e0b',
  ROUTED: '#14b8a6',
  COMPLETED: '#10b981',
  FAILED: '#ef4444',
  SKIPPED: '#6b7280',
};

interface TraceTimelineProps {
  trace: StepRecord[];
  activeStep: number | null;
  onStepClick: (index: number) => void;
}

export const TraceTimeline: React.FC<TraceTimelineProps> = ({ trace, activeStep, onStepClick }) => {
  if (!trace || trace.length === 0) return null;

  return (
    <Box sx={{
      borderTop: '1px solid',
      borderColor: 'divider',
      bgcolor: 'background.paper',
      overflowX: 'auto',
      flexShrink: 0,
    }}>
      <Box sx={{
        display: 'flex',
        alignItems: 'stretch',
        minWidth: 'max-content',
        px: 2,
        py: 1.5,
        gap: 0
      }}>
        {trace.map((step, i) => {
          const meta = NODE_TYPE_META[step.nodeType?.toUpperCase()] || { color: '#6366f1', icon: null };
          const isActive = activeStep === i;
          const isCompleted = step.status === 'COMPLETED' || step.status === 'EVALUATED' || step.status === 'ROUTED' || step.status === 'ENTERED';
          const statusColor = STEP_STATUS_COLORS[step.status] || '#6366f1';

          return (
            <React.Fragment key={i}>
              {/* Connector line */}
              {i > 0 && (
                <Box sx={{
                  display: 'flex', alignItems: 'center', px: 0.5
                }}>
                  <Box sx={{
                    height: 2,
                    width: 24,
                    bgcolor: isCompleted ? meta.color + '60' : 'action.disabledBackground',
                    position: 'relative',
                    overflow: 'hidden',
                    borderRadius: 1,
                    '&::after': isCompleted ? {
                      content: '""',
                      position: 'absolute',
                      top: 0, left: '-100%',
                      width: '100%', height: '100%',
                      bgcolor: meta.color,
                      animation: `flowAnim 1.5s linear ${i * 0.15}s forwards`,
                    } : {}
                  }} />
                </Box>
              )}

              {/* Step pill */}
              <Tooltip
                title={
                  <Box>
                    <Typography variant="caption" sx={{ fontWeight: 800, display: 'block' }}>{step.label}</Typography>
                    <Typography variant="caption" color="text.secondary">{step.notes}</Typography>
                    {step.expression && (
                      <Typography variant="caption" sx={{ display: 'block', fontFamily: 'monospace', color: '#818cf8', mt: 0.5 }}>
                        {step.expression} → {JSON.stringify(step.expressionResult)}
                      </Typography>
                    )}
                  </Box>
                }
                placement="top"
              >
                <Box
                  onClick={() => onStepClick(i)}
                  sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: 0.5,
                    cursor: 'pointer',
                    minWidth: 72,
                    transition: 'all 0.2s',
                    '&:hover': { transform: 'translateY(-2px)' },
                  }}
                >
                  {/* Node circle */}
                  <Box sx={{
                    width: isActive ? 38 : 32,
                    height: isActive ? 38 : 32,
                    borderRadius: '50%',
                    bgcolor: 'background.default',
                    border: `2px solid ${isActive ? meta.color : meta.color + '60'}`,
                    boxShadow: isActive ? `0 0 16px ${meta.color}80` : 'none',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: meta.color,
                    transition: 'all 0.2s',
                    position: 'relative',
                  }}>
                    {meta.icon}
                    {/* Step index badge */}
                    <Box sx={{
                      position: 'absolute',
                      top: -6, right: -6,
                      width: 14, height: 14,
                      borderRadius: '50%',
                      bgcolor: statusColor,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}>
                      <Typography sx={{ fontSize: '7px', fontWeight: 900, color: '#000' }}>{i + 1}</Typography>
                    </Box>
                  </Box>

                  {/* Label */}
                  <Typography
                    variant="caption"
                    sx={{
                      fontSize: '9px',
                      fontWeight: isActive ? 800 : 600,
                      color: isActive ? meta.color : 'text.secondary',
                      textAlign: 'center',
                      maxWidth: 70,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      display: 'block'
                    }}
                  >
                    {step.label || step.nodeType}
                  </Typography>

                  {/* Duration */}
                  {step.durationMs !== undefined && (
                    <Typography variant="caption" sx={{ fontSize: '8px', color: 'text.secondary' }}>
                      {step.durationMs}ms
                    </Typography>
                  )}
                </Box>
              </Tooltip>
            </React.Fragment>
          );
        })}
      </Box>

      <style>{`
        @keyframes flowAnim {
          0% { left: -100%; }
          100% { left: 0; }
        }
      `}</style>
    </Box>
  );
};
