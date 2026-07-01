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
import AccountTreeIcon from '@mui/icons-material/AccountTree';

// Base node wrapper for styles - reduced size
const NodeWrapper: React.FC<{
  borderColor: string;
  glowColor: string;
  selected?: boolean;
  children: React.ReactNode;
}> = ({ borderColor, glowColor, selected, children }) => (
  <Card sx={{
    minWidth: 120,
    maxWidth: 180,
    bgcolor: 'transparent',
    border: `1px solid ${selected ? '#6366f1' : borderColor}`,
    boxShadow: selected
      ? '0 0 10px rgba(99, 102, 241, 0.5)'
      : `0 2px 10px rgba(${glowColor}, 0.12)`,
    borderRadius: 2,
    overflow: 'visible',
    transition: 'all 0.2s ease-in-out',
    '&:hover': {
      boxShadow: `0 0 10px rgba(${glowColor}, 0.35)`,
      transform: 'scale(1.01)'
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
        width: 36,
        height: 36,
        borderRadius: '50%',
        bgcolor: 'transparent',
        border: `2px solid ${selected ? '#6366f1' : '#10b981'}`,
        boxShadow: selected ? '0 0 10px rgba(99,102,241,0.5)' : '0 0 8px rgba(16,185,129,0.3)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'all 0.2s'
      }}>
        <PlayArrowIcon sx={{ color: '#10b981', fontSize: 18 }} />
      </Box>
      <Typography variant="caption" sx={{ mt: 0.2, fontWeight: 700, fontSize: '8px', color: '#10b981' }}>START</Typography>
      <Handle type="source" position={Position.Bottom} style={{ background: '#10b981', width: 6, height: 6 }} />
    </Box>
  );
};

// 2. End Node
export const EndNode: React.FC<any> = ({ selected }) => {
  return (
    <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Handle type="target" position={Position.Top} style={{ background: '#ef4444', width: 6, height: 6 }} />
      <Box sx={{
        width: 36,
        height: 36,
        borderRadius: '50%',
        bgcolor: 'transparent',
        border: `2px solid ${selected ? '#6366f1' : '#ef4444'}`,
        boxShadow: selected ? '0 0 10px rgba(99,102,241,0.5)' : '0 0 8px rgba(239,68,68,0.3)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'all 0.2s'
      }}>
        <CancelIcon sx={{ color: '#ef4444', fontSize: 18 }} />
      </Box>
      <Typography variant="caption" sx={{ mt: 0.2, fontWeight: 700, fontSize: '8px', color: '#ef4444' }}>END</Typography>
    </Box>
  );
};

// 3. Rule Evaluation Node
export const RuleNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(99, 102, 241, 0.4)" glowColor="99, 102, 241" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#6366f1', width: 6, height: 6 }} />
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <RuleIcon sx={{ color: '#6366f1', fontSize: 18 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: 'primary.light', fontWeight: 800, fontSize: '8px', display: 'block', lineHeight: 1 }}>RULE EVALUATION</Typography>
          <Typography sx={{ fontWeight: 700, fontSize: '11px', lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Unconfigured Rule'}
          </Typography>
          {data.ruleId && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '8px', lineHeight: 1 }}>ID: {data.ruleId}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#6366f1', width: 6, height: 6 }} />
    </NodeWrapper>
  );
};

// 4. Decision Node
export const DecisionNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(245, 158, 11, 0.4)" glowColor="245, 158, 11" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#f59e0b', width: 6, height: 6 }} />
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <HelpOutlineIcon sx={{ color: '#f59e0b', fontSize: 18 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: 'warning.light', fontWeight: 800, fontSize: '8px', display: 'block', lineHeight: 1 }}>DECISION BRANCH</Typography>
          <Typography sx={{ fontWeight: 700, fontSize: '11px', lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Branch Condition'}
          </Typography>
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f59e0b', width: 6, height: 6 }} />
    </NodeWrapper>
  );
};

// 5. Bucket Node
export const BucketNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(168, 85, 247, 0.4)" glowColor="168, 85, 247" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#a855f7', width: 6, height: 6 }} />
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <ShoppingBagIcon sx={{ color: '#a855f7', fontSize: 18 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '8px', display: 'block', color: '#c084fc', lineHeight: 1 }}>BUCKET TASK</Typography>
          <Typography sx={{ fontWeight: 700, fontSize: '11px', lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Business Bucket'}
          </Typography>
          {data.bucketId && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '8px', lineHeight: 1 }}>ID: {data.bucketId}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#a855f7', width: 6, height: 6 }} />
    </NodeWrapper>
  );
};

// 6. Timer Node
export const TimerNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(20, 184, 166, 0.4)" glowColor="20, 184, 166" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#14b8a6', width: 6, height: 6 }} />
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <AccessAlarmIcon sx={{ color: '#14b8a6', fontSize: 18 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: 'secondary.light', fontWeight: 800, fontSize: '8px', display: 'block', lineHeight: 1 }}>TIMER DELAY</Typography>
          <Typography sx={{ fontWeight: 700, fontSize: '11px', lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || '10s Delay'}
          </Typography>
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#14b8a6', width: 6, height: 6 }} />
    </NodeWrapper>
  );
};

// 7. Parallel Node
export const ParallelNode: React.FC<any> = ({ selected }) => {
  return (
    <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Handle type="target" position={Position.Top} style={{ background: '#f97316', width: 6, height: 6 }} />
      <Box sx={{
        width: 60,
        height: 6,
        bgcolor: 'transparent',
        border: `1px solid ${selected ? '#6366f1' : '#f97316'}`,
        boxShadow: selected ? '0 0 8px #6366f1' : '0 0 5px #f97316',
        borderRadius: 0.5
      }} />
      <Typography variant="caption" sx={{ mt: 0.1, fontWeight: 700, fontSize: '7px', color: '#f97316' }}>PARALLEL SPLIT</Typography>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f97316', width: 6, height: 6 }} />
    </Box>
  );
};

// 8. Join Node
export const JoinNode: React.FC<any> = ({ selected }) => {
  return (
    <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Handle type="target" position={Position.Top} style={{ background: '#f97316', width: 6, height: 6 }} />
      <Box sx={{
        width: 60,
        height: 6,
        bgcolor: 'transparent',
        border: `1px solid ${selected ? '#6366f1' : '#f97316'}`,
        boxShadow: selected ? '0 0 8px #6366f1' : '0 0 5px #f97316',
        borderRadius: 0.5
      }} />
      <Typography variant="caption" sx={{ mt: 0.1, fontWeight: 700, fontSize: '7px', color: '#f97316' }}>JOIN CONVERGE</Typography>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f97316', width: 6, height: 6 }} />
    </Box>
  );
};

// 9. Sub-Workflow Node
export const SubWorkflowNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(59, 130, 246, 0.4)" glowColor="59, 130, 246" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#3b82f6', width: 6, height: 6 }} />
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <AccountTreeIcon sx={{ color: '#3b82f6', fontSize: 18 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '8px', display: 'block', color: '#60a5fa', lineHeight: 1 }}>SUB-WORKFLOW</Typography>
          <Typography sx={{ fontWeight: 700, fontSize: '11px', lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Sub-Workflow Call'}
          </Typography>
          {data.childWorkflowKey && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '8px', lineHeight: 1 }}>Key: {data.childWorkflowKey}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#3b82f6', width: 6, height: 6 }} />
    </NodeWrapper>
  );
};

// 10. Command Node
export const CommandNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(56, 189, 248, 0.4)" glowColor="56, 189, 248" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#38bdf8', width: 6, height: 6 }} />
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <TerminalIcon sx={{ color: '#38bdf8', fontSize: 18 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '8px', display: 'block', color: '#38bdf8', lineHeight: 1 }}>COMMAND EMITTER</Typography>
          <Typography sx={{ fontWeight: 700, fontSize: '11px', lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Emit Command'}
          </Typography>
          {(data.commandType || data.type) && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '8px', lineHeight: 1 }}>Type: {data.commandType || data.type}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#38bdf8', width: 6, height: 6 }} />
    </NodeWrapper>
  );
};

// 11. Wait Event Node
export const WaitEventNode: React.FC<any> = ({ data, selected }) => {
  return (
    <NodeWrapper borderColor="rgba(245, 158, 11, 0.4)" glowColor="245, 158, 11" selected={selected}>
      <Handle type="target" position={Position.Top} style={{ background: '#f59e0b', width: 6, height: 6 }} />
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, display: 'flex', alignItems: 'center', gap: 1 }}>
        <HourglassEmptyIcon sx={{ color: '#f59e0b', fontSize: 18 }} />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ fontWeight: 800, fontSize: '8px', display: 'block', color: '#f59e0b', lineHeight: 1 }}>WAIT EVENT</Typography>
          <Typography sx={{ fontWeight: 700, fontSize: '11px', lineHeight: 1.2, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Wait for Event'}
          </Typography>
          {data.eventType && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '8px', lineHeight: 1 }}>Event: {data.eventType}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={Position.Bottom} style={{ background: '#f59e0b', width: 6, height: 6 }} />
    </NodeWrapper>
  );
};

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
