import React from 'react';
import { AppBar, Toolbar, Typography, Box, Select, MenuItem, FormControl, Chip, Button, IconButton, InputBase, Checkbox, ListItemText } from '@mui/material';
import { Menu, Search, Bell, User, Layers } from 'lucide-react';
import { useWorkflowStore } from '../store/workflowStore.js';
import { ViLogo } from './ViLogo.jsx';

const CIRCLE_OPTIONS = [
  { id: 101, label: '101 - MH (Maharashtra)', badge: 'MH', color: '#00A05A' },
  { id: 102, label: '102 - DL (Delhi)', badge: 'DL', color: '#0CADEF' },
  { id: 103, label: '103 - KA (Karnataka)', badge: 'KA', color: '#8b5cf6' },
  { id: 104, label: '104 - TN (Tamil Nadu)', badge: 'TN', color: '#ff6d00' },
  { id: 105, label: '105 - DL_NCR (Delhi NCR)', badge: 'NCR', color: '#d97706' },
  { id: 106, label: '106 - ROWB (West Bengal)', badge: 'WB', color: '#64748b' },
];

export const Navbar = () => {
  const { activeRole, setActiveRole, currentView, setView, toggleSidebar, selectedCircleIds, setSelectedCircleIds } = useWorkflowStore();

  const handleCircleChange = (event) => {
    const { target: { value } } = event;
    const newValues = typeof value === 'string' ? value.split(',') : value;
    setSelectedCircleIds(newValues);
  };
  return (<AppBar position="sticky" className="atlas-navbar" sx={{
    bgcolor: '#FFFFFF',
    color: '#2F3043',
    borderBottom: '1px solid #EAEAEC',
    boxShadow: 'none',
    height: 64, // Increased height for top navigation
  }}>
    <Toolbar className="atlas-navbar-toolbar" sx={{ minHeight: '64px !important', height: 64, px: '24px !important', display: 'flex', justifyContent: 'space-between' }}>

      {/* Left Side: Brand & Menu Toggle */}
      <Box className="atlas-navbar-left" sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <IconButton className="atlas-navbar-menu-toggle" edge="start" aria-label="menu" onClick={toggleSidebar} size="small" sx={{ color: '#595969', '&:hover': { bgcolor: '#F4F4F4' } }}>
          <Menu size={20} />
        </IconButton>

        <Box className="atlas-navbar-brand" sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <ViLogo size={34} className="atlas-navbar-logo-icon" />

          <Box className="atlas-navbar-title-group" sx={{ display: 'flex', flexDirection: 'column' }}>
            <Typography className="atlas-navbar-title" sx={{ fontWeight: 700, fontSize: '16px', color: '#2F3043', lineHeight: 1.2 }}>
              Postpaid
            </Typography>
            <Typography className="atlas-navbar-subtitle" sx={{ fontWeight: 500, fontSize: '11px', color: '#82838E', lineHeight: 1.2 }}>
              Workflow Engine
            </Typography>
          </Box>

          {/* Environment Badge */}
          <Chip className="atlas-navbar-env-badge" label="PRODUCTION" size="small" sx={{
            ml: 2,
            bgcolor: 'rgba(0, 160, 90, 0.1)',
            color: '#00A05A',
            fontWeight: 700,
            fontSize: '10px',
            height: 20,
            borderRadius: '4px'
          }} />
        </Box>
      </Box>

      {/* Center/Right Side Main Navigation & Utilities */}
      <Box className="atlas-navbar-right" sx={{ display: 'flex', alignItems: 'center', gap: 4 }}>

        <Box className="atlas-navbar-links" sx={{ display: 'flex', gap: 1 }}>
          <Button className={`atlas-navbar-link-btn ${currentView === 'dashboard' ? 'active' : ''}`} onClick={() => setView('dashboard')} sx={{
            color: currentView === 'dashboard' ? '#EE2737' : '#595969',
            fontWeight: 600,
            fontSize: '13px',
            height: 64,
            borderRadius: 0,
            px: 2,
            borderBottom: currentView === 'dashboard' ? '3px solid #EE2737' : '3px solid transparent',
            '&:hover': { bgcolor: 'transparent', color: '#EE2737' }
          }}>
            Dashboard
          </Button>
          <Button className={`atlas-navbar-link-btn ${currentView === 'manageWorkflows' ? 'active' : ''}`} onClick={() => setView('manageWorkflows')} sx={{
            color: currentView === 'manageWorkflows' ? '#EE2737' : '#595969',
            fontWeight: 600,
            fontSize: '13px',
            height: 64,
            borderRadius: 0,
            px: 2,
            borderBottom: currentView === 'manageWorkflows' ? '3px solid #EE2737' : '3px solid transparent',
            '&:hover': { bgcolor: 'transparent', color: '#EE2737' }
          }}>
            Manage Workflows
          </Button>
        </Box>

        {/* Search Box */}
        <Box className="atlas-navbar-search-container" sx={{
          display: 'flex',
          alignItems: 'center',
          bgcolor: '#F4F4F4',
          borderRadius: '6px',
          px: 1.5,
          py: 0.5,
          width: 200,
          border: '1px solid transparent',
          '&:hover, &:focus-within': {
            borderColor: '#ACACB4',
            bgcolor: '#FFFFFF'
          }
        }}>
          <Search size={16} color="#82838E" />
          <InputBase className="atlas-navbar-search-input" placeholder="Search..." sx={{ ml: 1, flex: 1, fontSize: '13px', color: '#2F3043' }} />
        </Box>

        {/* Global Circle ID Selector */}
        <Box className="atlas-navbar-circle-container" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <FormControl size="small" variant="outlined" sx={{ minWidth: 160 }}>
            <Select
              className="atlas-navbar-circle-select"
              id="circle-select"
              multiple
              value={selectedCircleIds}
              onChange={handleCircleChange}
              displayEmpty
              renderValue={(selected) => {
                if (!selected || selected.length === 0) {
                  return <Typography sx={{ fontSize: '13px', fontWeight: 600, color: '#2F3043' }}>All Circles</Typography>;
                }
                return (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {selected.map((val) => {
                      const opt = CIRCLE_OPTIONS.find(c => c.id === Number(val));
                      return (
                        <Chip key={val} label={opt ? opt.badge : val} size="small" sx={{ height: 20, fontSize: '10px', fontWeight: 700, bgcolor: opt?.color || '#2F3043', color: '#fff' }} />
                      );
                    })}
                  </Box>
                );
              }}
              sx={{
                height: 32,
                fontSize: '13px',
                fontWeight: 600,
                color: '#2F3043',
                bgcolor: '#F4F4F6',
                borderRadius: '6px',
                '.MuiOutlinedInput-notchedOutline': {
                  borderColor: 'rgba(47, 48, 67, 0.2)',
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  borderColor: '#2F3043',
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  borderColor: '#EE2737',
                },
              }}
            >
              {CIRCLE_OPTIONS.map((opt) => (
                <MenuItem key={opt.id} value={opt.id} sx={{ fontSize: '13px', py: 0.5 }}>
                  <Checkbox checked={selectedCircleIds.indexOf(opt.id) > -1} size="small" />
                  <Chip size="small" label={opt.badge} sx={{ bgcolor: opt.color, color: '#fff', fontSize: '9px', fontWeight: 700, height: 18, mr: 1, borderRadius: '3px' }} />
                  <ListItemText primary={opt.label} primaryTypographyProps={{ fontSize: '13px', fontWeight: 500 }} />
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>

        {/* Governance Role Selector */}
        <Box className="atlas-navbar-role-container" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <FormControl size="small" variant="outlined" sx={{ minWidth: 120 }}>
            <Select className="atlas-navbar-role-select" id="role-select" value={activeRole} onChange={(e) => setActiveRole(e.target.value)} sx={{
              height: 32,
              fontSize: '13px',
              fontWeight: 500,
              color: '#2F3043',
              bgcolor: '#FFFFFF',
              borderRadius: '6px',
              '.MuiOutlinedInput-notchedOutline': {
                borderColor: '#EAEAEC',
              },
              '&:hover .MuiOutlinedInput-notchedOutline': {
                borderColor: '#ACACB4',
              },
              '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                borderColor: '#EE2737', // Crimson focus
              },
            }}>
              <MenuItem value="Admin" sx={{ fontSize: '13px', py: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Chip size="small" label="AD" sx={{ bgcolor: '#EE2737', color: '#fff', fontSize: '10px', fontWeight: 600, height: 20, borderRadius: '4px' }} />
                  Admin (Full Access)
                </Box>
              </MenuItem>
              <MenuItem value="Author" sx={{ fontSize: '13px', py: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Chip size="small" label="AU" sx={{ bgcolor: '#0CADEF', color: '#fff', fontSize: '10px', fontWeight: 600, height: 20, borderRadius: '4px' }} />
                  Author
                </Box>
              </MenuItem>
              <MenuItem value="Reviewer" sx={{ fontSize: '13px', py: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Chip size="small" label="RV" sx={{ bgcolor: '#FFC600', color: '#111622', fontSize: '10px', fontWeight: 600, height: 20, borderRadius: '4px' }} />
                  Reviewer
                </Box>
              </MenuItem>
              <MenuItem value="Publisher" sx={{ fontSize: '13px', py: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Chip size="small" label="PB" sx={{ bgcolor: '#2F3043', color: '#fff', fontSize: '10px', fontWeight: 600, height: 20, borderRadius: '4px' }} />
                  Publisher
                </Box>
              </MenuItem>
            </Select>
          </FormControl>
        </Box>

        {/* Utilities */}
        <Box className="atlas-navbar-utilities" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <IconButton className="atlas-navbar-utility-btn" size="small" sx={{ color: '#595969', '&:hover': { bgcolor: '#F4F4F4', color: '#2F3043' } }}>
            <Bell size={18} />
          </IconButton>
          <IconButton className="atlas-navbar-utility-btn" size="small" sx={{ color: '#595969', '&:hover': { bgcolor: '#F4F4F4', color: '#2F3043' } }}>
            <User size={18} />
          </IconButton>
        </Box>

      </Box>
    </Toolbar>
  </AppBar>);
};
