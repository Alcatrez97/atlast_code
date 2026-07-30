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
// Base node wrapper for styles - supports orientation
const NodeWrapper = ({ borderColor, selected, children }) => {
    return (<Card sx={{
            minWidth: 80,
            maxWidth: 160,
            bgcolor: 'background.paper',
            border: `1px solid ${selected ? '#EE2737' : borderColor}`,
            boxShadow: selected
                ? '0 0 0 2px rgba(238, 39, 55, 0.4)'
                : '0 2px 4px rgba(0, 0, 0, 0.08)',
            borderRadius: 2,
            overflow: 'visible',
            position: 'relative',
            transition: 'all 0.2s ease-in-out',
            '&:hover': {
                boxShadow: selected
                    ? '0 0 0 2px rgba(238, 39, 55, 0.4), 0 4px 8px rgba(0, 0, 0, 0.12)'
                    : '0 4px 8px rgba(0, 0, 0, 0.1)',
                transform: 'scale(1.01)'
            }
        }}>
      {children}
    </Card>);
};
// 1. Start Node
export const StartNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#0DA05A'; // Brand Active Green
    return (<Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Box sx={{
            width: 14,
            height: 14,
            borderRadius: '50%',
            bgcolor: 'background.paper',
            border: `2px solid ${selected ? '#EE2737' : color}`,
            boxShadow: selected ? '0 0 0 2px rgba(238,39,55,0.4)' : 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.2s'
        }}>
        <PlayArrowIcon sx={{ color, fontSize: 10 }}/>
      </Box>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </Box>);
};
// 2. End Node
export const EndNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#B30E0E'; // Brand Error/Stop Deep Red
    return (<Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <Box sx={{
            width: 14,
            height: 14,
            borderRadius: '50%',
            bgcolor: 'background.paper',
            border: `2px solid ${selected ? '#EE2737' : color}`,
            boxShadow: selected ? '0 0 0 2px rgba(238,39,55,0.4)' : 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.2s'
        }}>
        <CancelIcon sx={{ color, fontSize: 10 }}/>
      </Box>
    </Box>);
};
// 3. Rule Evaluation Node
export const RuleNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#00A9FF'; // Brand Accent Blue
    return (<NodeWrapper borderColor="rgba(0, 169, 255, 0.4)" glowColor="0, 169, 255" selected={selected} orientation={data?.orientation}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <CardContent sx={{ p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18 }}>
        <RuleIcon sx={{ color, fontSize: 10 }}/>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '10px', lineHeight: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Unconfigured Rule'}
          </Typography>
          {false && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '7px', lineHeight: 1 }}>ID: {data.ruleId}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </NodeWrapper>);
};
// 4. Decision Node
export const DecisionNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#FFC600'; // Brand Mustard
    return (<NodeWrapper borderColor="rgba(255, 198, 0, 0.4)" glowColor="255, 198, 0" selected={selected} orientation={data?.orientation}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <CardContent sx={{ p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18 }}>
        <HelpOutlineIcon sx={{ color, fontSize: 10 }}/>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '10px', lineHeight: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Branch Condition'}
          </Typography>
        </Box>
      </CardContent>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </NodeWrapper>);
};
// 5. Bucket Node
export const BucketNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#5F004B'; // Brand CTA Purple
    return (<NodeWrapper borderColor="rgba(95, 0, 75, 0.4)" glowColor="95, 0, 75" selected={selected} orientation={data?.orientation}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <CardContent sx={{ p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18 }}>
        <ShoppingBagIcon sx={{ color, fontSize: 10 }}/>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '10px', lineHeight: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Business Bucket'}
          </Typography>
          {false && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '7px', lineHeight: 1 }}>ID: {data.bucketId}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </NodeWrapper>);
};
// 6. Timer Node
export const TimerNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#DF720E'; // Brand Pending Orange
    return (<NodeWrapper borderColor="rgba(223, 114, 14, 0.4)" glowColor="223, 114, 14" selected={selected} orientation={data?.orientation}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <CardContent sx={{ p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18 }}>
        <AccessAlarmIcon sx={{ color, fontSize: 10 }}/>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '10px', lineHeight: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || '10s Delay'}
          </Typography>
        </Box>
      </CardContent>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </NodeWrapper>);
};
// 7. Parallel Node
export const ParallelNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#82838E'; // Slate 60%
    return (<Box sx={{ position: 'relative', display: 'flex', flexDirection: isHoriz ? 'row' : 'column', alignItems: 'center' }}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <Box sx={{
            width: isHoriz ? 5 : 50,
            height: isHoriz ? 50 : 5,
            bgcolor: 'background.paper',
            border: `1px solid ${selected ? '#EE2737' : color}`,
            boxShadow: 'none',
            borderRadius: 0.5
        }}/>
      <Typography variant="caption" sx={{
            ml: isHoriz ? 0.5 : 0,
            mt: isHoriz ? 0 : 0.1,
            fontWeight: 700,
            fontSize: '6px',
            color,
            writingMode: isHoriz ? 'vertical-lr' : 'horizontal-tb'
        }}>PARALLEL SPLIT</Typography>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </Box>);
};
// 8. Join Node
export const JoinNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#82838E'; // Slate 60%
    return (<Box sx={{ position: 'relative', display: 'flex', flexDirection: isHoriz ? 'row' : 'column', alignItems: 'center' }}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <Box sx={{
            width: isHoriz ? 5 : 50,
            height: isHoriz ? 50 : 5,
            bgcolor: 'background.paper',
            border: `1px solid ${selected ? '#EE2737' : color}`,
            boxShadow: 'none',
            borderRadius: 0.5
        }}/>
      <Typography variant="caption" sx={{
            ml: isHoriz ? 0.5 : 0,
            mt: isHoriz ? 0 : 0.1,
            fontWeight: 700,
            fontSize: '6px',
            color,
            writingMode: isHoriz ? 'vertical-lr' : 'horizontal-tb'
        }}>JOIN CONVERGE</Typography>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </Box>);
};
// 9. Sub-Workflow Node
export const SubWorkflowNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#00A9FF'; // Brand Blue Accent
    return (<NodeWrapper borderColor="rgba(0, 169, 255, 0.4)" glowColor="0, 169, 255" selected={selected} orientation={data?.orientation}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <CardContent sx={{ p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18 }}>
        <AccountTreeIcon sx={{ color, fontSize: 10 }}/>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '10px', lineHeight: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Sub-Workflow Call'}
          </Typography>
          {false && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '7px', lineHeight: 1 }}>Key: {data.childWorkflowKey}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </NodeWrapper>);
};
// 10. Command Node
export const CommandNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#EE2737'; // Brand Crimson
    return (<NodeWrapper borderColor="rgba(238, 39, 55, 0.4)" glowColor="238, 39, 55" selected={selected} orientation={data?.orientation}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <CardContent sx={{ p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18 }}>
        <TerminalIcon sx={{ color, fontSize: 10 }}/>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '10px', lineHeight: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Emit Command'}
          </Typography>
          {false && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '7px', lineHeight: 1 }}>Type: {data.commandType || data.type}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </NodeWrapper>);
};
// 11. Wait Event Node
export const WaitEventNode = ({ data, selected }) => {
    const isHoriz = data?.orientation === 'horizontal';
    const color = '#DF720E'; // Brand Pending Orange
    return (<NodeWrapper borderColor="rgba(223, 114, 14, 0.4)" glowColor="223, 114, 14" selected={selected} orientation={data?.orientation}>
      <Handle type="target" position={isHoriz ? Position.Left : Position.Top} style={{ background: color, width: 6, height: 6 }}/>
      <CardContent sx={{ p: 0, pl: 0.5, pr: 0.5, '&:last-child': { pb: 0 }, display: 'flex', alignItems: 'center', gap: 0.5, height: 18 }}>
        <HourglassEmptyIcon sx={{ color, fontSize: 10 }}/>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '10px', lineHeight: 1, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            {data.label || 'Wait for Event'}
          </Typography>
          {false && <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', fontSize: '7px', lineHeight: 1 }}>Event: {data.eventType}</Typography>}
        </Box>
      </CardContent>
      <Handle type="source" position={isHoriz ? Position.Right : Position.Bottom} style={{ background: color, width: 6, height: 6 }}/>
    </NodeWrapper>);
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
