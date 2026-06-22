import React from 'react';
import { Handle, Position } from '@xyflow/react';
import { Box, Typography, Card, CardContent } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CancelIcon from '@mui/icons-material/Cancel';
import HelpOutlineIcon from '@mui/icons-material/Help';
import RuleIcon from '@mui/icons-material/Rule';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import AccessAlarmIcon from '@mui/icons-material/AccessAlarm';
import TerminalIcon from '@mui/icons-material/Terminal';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';

// Base node wrapper for styles
const NodeWrapper: React.FC<{
  borderColor: string;
  glowColor: string;
  selected?: boolean;
  children: React.ReactNode;
}> = ({ borderColor, glowColor, selected, children }) => (
  <Card sx={{
    minWidth: 160,
    maxWidth: 240,
    bgcolor: 'transparent',
    border: `1.5px solid ${selected ? '#6366f1' : borderColor}`,
    boxShadow: selected
      ? '0 0 16px rgba(99, 102, 241, 0.6)'
      : `0 4px 16px rgba(${glowColor}, 0.15)`,
    borderRadius: 3,
    overflow: 'visible',
    transition: 'all 0.2s ease-in-out',
    '&:hover': {
      boxShadow: `0 0 14px rgba(${glowColor}, 0.4)`,
      transform: 'scale(1.02)'
    }
  }}>
    {children}
  </Card>
);

// 1. Start Node
export const StartNode: React.FC<any> = ({ selected }) => {
  return (
    <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Box sx={{
        width: 44,
        height: 44,
        borderRadius: '50%',
        bgcolor: 'transparent',
        border: `2px solid ${selected ? '#6366f1' : '#10b981'}`,
        boxShadow: selected ? '0 0 14px rgba(99,102,241,0.6)' : '0 0 12px rgba(16,185,129,0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'all 0.2s'
      }}>
        <PlayArrowIcon sx={{ color: '#10b981', fontSize: 24 }} />
      </Box>
      <Typography variant="caption" sx={{ mt: 0.5, fontWeight: 700, color: '#10b981' }}>START</Typography>
      <Handle type="source" position={Position.Bottom} style={{ background: '#10b981', width: 8, height: 8 }} />
    </Box>
  );
};

// 2. End Node
export const EndNode: React.FC<any> = ({ selected }) => {
  return (
    <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Handle type="target" position={Position.Top} style={{ background: '#ef4444', width: 8, height: 8 }} />
      <Box sx={{
        width: 44,
        height: 44,
        borderRadius: '50%',
        bgcolor: 'transparent',
        border: `2px solid ${selected ? '#6366f1' : '#ef4444'}`,
        boxShadow: selected ? '0 0 14px rgba(99,102,241,0.6)' : '0 0 12px rgba(239,68,68,0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'all 0.2s'
      }}>
        <CancelIcon sx={{ color: '#ef4444', fontSize: 24 }} />
      </Box>
      <Typography variant="caption" sx={{ mt: 0.5, fontWeight: 700, color: '#ef4444' }}>END</Typography>
    </Box>
  );
};

// 3. Rule Evaluation Node
export const RuleNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(99, 102, 241, 0.4)" glowColor="99, 102, 241" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#6366f1' }} />
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <RuleIcon sx={{ color: '#6366f1', fontSize: 20 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: 'primary.light', fontWeight: 800, fontSize: '9px', display: 'block' }}>RULE EVALUATION</Typography>
          <Typography variant="body2" noWrap sx={{ fontWeight: 700, textOverflow: 'ellipsis', overflow: 'hidden' }}>
            {data.label || 'Unconfigured Rule'}
          </Typography>
          {data.ruleId && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '9px' }}>ID: {data.ruleId}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#6366f1' }} />
    </NodeWrapper>
  );
};

// 4. Decision Node
export const DecisionNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(245, 158, 11, 0.4)" glowColor="245, 158, 11" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#f59e0b' }} />
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <HelpOutlineIcon sx={{ color: '#f59e0b', fontSize: 20 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: 'warning.light', fontWeight: 800, fontSize: '9px', display: 'block' }}>DECISION BRANCH</Typography>
          <Typography variant="body2" noWrap sx={{ fontWeight: 700 }}>
            {data.label || 'Branch Condition'}
          </Typography>
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f59e0b' }} />
    </NodeWrapper>
  );
};

// 5. Bucket Node
export const BucketNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(168, 85, 247, 0.4)" glowColor="168, 85, 247" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#a855f7' }} />
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <ShoppingBagIcon sx={{ color: '#a855f7', fontSize: 20 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '9px', display: 'block', color: '#c084fc' }}>BUCKET TASK</Typography>
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            {data.label || 'Business Bucket'}
          </Typography>
          {data.bucketId && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '9px' }}>ID: {data.bucketId}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#a855f7' }} />
    </NodeWrapper>
  );
};

// 6. Timer Node
export const TimerNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(20, 184, 166, 0.4)" glowColor="20, 184, 166" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#14b8a6' }} />
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <AccessAlarmIcon sx={{ color: '#14b8a6', fontSize: 20 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: 'secondary.light', fontWeight: 800, fontSize: '9px', display: 'block' }}>TIMER DELAY</Typography>
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            {data.label || '10s Delay'}
          </Typography>
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#14b8a6' }} />
    </NodeWrapper>
  );
};

// 7. Parallel Node
export const ParallelNode: React.FC<any> = ({ selected }) => {
  return (
    <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Handle type="target" position={Position.Top} style={{ background: '#f97316' }} />
      <Box sx={{
        width: 80,
        height: 8,
        bgcolor: 'transparent',
        border: `1.5px solid ${selected ? '#6366f1' : '#f97316'}`,
        boxShadow: selected ? '0 0 10px #6366f1' : '0 0 6px #f97316',
        borderRadius: 1
      }} />
      <Typography variant="caption" sx={{ mt: 0.2, fontWeight: 700, fontSize: '8px', color: '#f97316' }}>PARALLEL SPLIT</Typography>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f97316' }} />
    </Box>
  );
};

// 8. Join Node
export const JoinNode: React.FC<any> = ({ selected }) => {
  return (
    <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Handle type="target" position={Position.Top} style={{ background: '#f97316' }} />
      <Box sx={{
        width: 80,
        height: 8,
        bgcolor: 'transparent',
        border: `1.5px solid ${selected ? '#6366f1' : '#f97316'}`,
        boxShadow: selected ? '0 0 10px #6366f1' : '0 0 6px #f97316',
        borderRadius: 1
      }} />
      <Typography variant="caption" sx={{ mt: 0.2, fontWeight: 700, fontSize: '8px', color: '#f97316' }}>JOIN CONVERGE</Typography>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f97316' }} />
    </Box>
  );
};

// 9. Sub-Workflow Node
export const SubWorkflowNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(59, 130, 246, 0.4)" glowColor="59, 130, 246" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#3b82f6' }} />
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <AccountTreeIcon sx={{ color: '#3b82f6', fontSize: 20 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '9px', display: 'block', color: '#60a5fa' }}>SUB-WORKFLOW</Typography>
          <Typography variant="body2" sx={{ fontWeight: 700, textOverflow: 'ellipsis', overflow: 'hidden' }}>
            {data.label || 'Sub-Workflow Call'}
          </Typography>
          {data.childWorkflowKey && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '9px' }}>Key: {data.childWorkflowKey}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#3b82f6' }} />
    </NodeWrapper>
  );
};

export const CommandNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(56, 189, 248, 0.4)" glowColor="56, 189, 248" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#38bdf8' }} />
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <TerminalIcon sx={{ color: '#38bdf8', fontSize: 20 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '9px', display: 'block', color: '#38bdf8' }}>COMMAND EMITTER</Typography>
          <Typography variant="body2" sx={{ fontWeight: 700, textOverflow: 'ellipsis', overflow: 'hidden' }}>
            {data.label || 'Emit Command'}
          </Typography>
          {(data.commandType || data.type) && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '9px' }}>Type: {data.commandType || data.type}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#38bdf8' }} />
    </NodeWrapper>
  );
};

export const WaitEventNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(245, 158, 11, 0.4)" glowColor="245, 158, 11" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#f59e0b' }} />
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <HourglassEmptyIcon sx={{ color: '#f59e0b', fontSize: 20 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '9px', display: 'block', color: '#f59e0b' }}>WAIT EVENT</Typography>
          <Typography variant="body2" sx={{ fontWeight: 700, textOverflow: 'ellipsis', overflow: 'hidden' }}>
            {data.label || 'Wait for Event'}
          </Typography>
          {data.eventType && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '9px' }}>Event: {data.eventType}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f59e0b' }} />
    </NodeWrapper>
  );
};

import AccountTreeIcon from '@mui/icons-material/AccountTree';

// Mapping dictionary for React Flow node types config
export const nodeTypes = {
  START: StartNode,
  END: EndNode,
  RULE: RuleNode,
  DECISION: DecisionNode,
  BUCKET: BucketNode,
  TIMER: TimerNode,
  PARALLEL: ParallelNode,
  JOIN: JoinNode,
  SUB_WORKFLOW: SubWorkflowNode,
  COMMAND: CommandNode,
  WAIT_EVENT: WaitEventNode
};
