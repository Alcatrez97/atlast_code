/**
 * Single source of truth for Telecom Circle and Zone mappings across the UI.
 * Configured per Telecom Zone hierarchy.
 */

export const CIRCLE_OPTIONS = [
  // Zone-1
  { id: 4, name: 'Karnataka', zone: 'Zone-1', badge: 'KA', label: '4 - Karnataka (Zone-1)', color: '#8b5cf6' },
  { id: 5, name: 'Chennai', zone: 'Zone-1', badge: 'CHN', label: '5 - Chennai (Zone-1)', color: '#0ea5e9' },
  { id: 10, name: 'Tamilnadu', zone: 'Zone-1', badge: 'TN', label: '10 - Tamilnadu (Zone-1)', color: '#ff6d00' },
  { id: 11, name: 'Kerala', zone: 'Zone-1', badge: 'KER', label: '11 - Kerala (Zone-1)', color: '#10b981' },
  { id: 13, name: 'Asam', zone: 'Zone-1', badge: 'ASM', label: '13 - Asam (Zone-1)', color: '#06b6d4' },
  { id: 14, name: 'NE', zone: 'Zone-1', badge: 'NE', label: '14 - NE (Zone-1)', color: '#14b8a6' },
  { id: 15, name: 'MPCG', zone: 'Zone-1', badge: 'MPCG', label: '15 - MPCG (Zone-1)', color: '#f59e0b' },
  { id: 19, name: 'Punjab', zone: 'Zone-1', badge: 'PB', label: '19 - Punjab (Zone-1)', color: '#ec4899' },
  { id: 22, name: 'HP', zone: 'Zone-1', badge: 'HP', label: '22 - HP (Zone-1)', color: '#6366f1' },
  { id: 23, name: 'JNK', zone: 'Zone-1', badge: 'JNK', label: '23 - JNK (Zone-1)', color: '#a855f7' },

  // Zone-2
  { id: 1, name: 'Mumbai', zone: 'Zone-2', badge: 'MUM', label: '1 - Mumbai (Zone-2)', color: '#00A05A' },
  { id: 3, name: 'Andra Pradesh', zone: 'Zone-2', badge: 'AP', label: '3 - Andra Pradesh (Zone-2)', color: '#3b82f6' },
  { id: 6, name: 'Kolkata', zone: 'Zone-2', badge: 'KOL', label: '6 - Kolkata (Zone-2)', color: '#64748b' },
  { id: 7, name: 'ROB', zone: 'Zone-2', badge: 'ROB', label: '7 - ROB (Zone-2)', color: '#d97706' },
  { id: 21, name: 'UPW', zone: 'Zone-2', badge: 'UPW', label: '21 - UPW (Zone-2)', color: '#e11d48' },

  // Zone-3
  { id: 2, name: 'Gujarat', zone: 'Zone-3', badge: 'GUJ', label: '2 - Gujarat (Zone-3)', color: '#059669' },
  { id: 9, name: 'MAG', zone: 'Zone-3', badge: 'MAG', label: '9 - MAG (Zone-3)', color: '#7c3aed' },
  { id: 16, name: 'Bihar', zone: 'Zone-3', badge: 'BIH', label: '16 - Bihar (Zone-3)', color: '#ea580c' },

  // Zone-4
  { id: 8, name: 'Rajasthan', zone: 'Zone-4', badge: 'RAJ', label: '8 - Rajasthan (Zone-4)', color: '#f97316' },
  { id: 12, name: 'Odisa', zone: 'Zone-4', badge: 'ODI', label: '12 - Odisa (Zone-4)', color: '#0284c7' },
  { id: 17, name: 'Delhi', zone: 'Zone-4', badge: 'DL', label: '17 - Delhi (Zone-4)', color: '#0CADEF' },
  { id: 18, name: 'Haryana', zone: 'Zone-4', badge: 'HAR', label: '18 - Haryana (Zone-4)', color: '#84cc16' },
  { id: 20, name: 'UPE', zone: 'Zone-4', badge: 'UPE', label: '20 - UPE (Zone-4)', color: '#db2777' },
];

export const ZONES = ['Zone-1', 'Zone-2', 'Zone-3', 'Zone-4'];

export const CIRCLES_BY_ZONE = {
  'Zone-1': CIRCLE_OPTIONS.filter(c => c.zone === 'Zone-1'),
  'Zone-2': CIRCLE_OPTIONS.filter(c => c.zone === 'Zone-2'),
  'Zone-3': CIRCLE_OPTIONS.filter(c => c.zone === 'Zone-3'),
  'Zone-4': CIRCLE_OPTIONS.filter(c => c.zone === 'Zone-4'),
};

export const getCircleById = (circleId) => {
  if (circleId == null) return null;
  const numId = Number(circleId);
  return CIRCLE_OPTIONS.find(c => c.id === numId) || {
    id: numId,
    name: `Circle ${circleId}`,
    zone: 'Unknown',
    badge: String(circleId),
    label: `Circle ${circleId}`,
    color: '#6366f1'
  };
};

export const getCircleLabel = (circleId) => {
  const c = getCircleById(circleId);
  return c ? c.label : `Circle ${circleId}`;
};

export const getCircleBadge = (circleId) => {
  const c = getCircleById(circleId);
  return c ? c.badge : String(circleId);
};

export const getCircleColor = (circleId) => {
  const c = getCircleById(circleId);
  return c ? c.color : '#6366f1';
};
