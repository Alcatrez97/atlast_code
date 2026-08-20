const fs = require('fs');
const path = require('path');

const iconMap = {
  'Add': 'Plus', 'Edit': 'Edit3', 'Delete': 'Trash2', 'Settings': 'Settings',
  'CheckCircle': 'CheckCircle', 'Error': 'AlertCircle', 'Warning': 'AlertTriangle',
  'Info': 'Info', 'ContentCopy': 'Copy', 'Close': 'X', 'Search': 'Search',
  'PlayArrow': 'Play', 'Stop': 'Square', 'MoreVert': 'MoreVertical',
  'Visibility': 'Eye', 'VisibilityOff': 'EyeOff', 'ArrowBack': 'ArrowLeft',
  'ArrowForward': 'ArrowRight', 'ExpandMore': 'ChevronDown', 'ExpandLess': 'ChevronUp',
  'Refresh': 'RefreshCw', 'Assignment': 'FileText', 'Inbox': 'Inbox', 'Rule': 'Sliders',
  'SettingsInputComponent': 'Plug', 'AutoMode': 'Activity', 'ListAlt': 'FileSpreadsheet',
  'Assessment': 'LayoutDashboard', 'History': 'History', 'NotificationsActive': 'Bell',
  'CloudQueue': 'Cloud', 'BugReport': 'Bug', 'Speed': 'Gauge', 'DeviceHub': 'Network',
  'HelpOutline': 'HelpCircle', 'Terminal': 'Terminal', 'Menu': 'Menu', 'Palette': 'Palette',
  'Drafts': 'Edit3', 'PendingActions': 'Clock', 'DataObject': 'Code', 'Http': 'Globe',
  'AccountTree': 'GitMerge', 'Functions': 'FunctionSquare', 'Timer': 'Timer', 'Send': 'Send',
  'Save': 'Save', 'Check': 'Check', 'CloseFullscreen': 'Minimize', 'OpenInFull': 'Maximize',
  'Code': 'Code2', 'Description': 'FileText', 'TaskAlt': 'CheckSquare', 'WarningAmber': 'AlertTriangle',
  'Article': 'FileText', 'TableChart': 'Table', 'Api': 'Webhook', 'Download': 'Download', 'Upload': 'Upload',
  'CloudUpload': 'UploadCloud'
};

function walk(dir) {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach(file => {
    file = path.join(dir, file);
    const stat = fs.statSync(file);
    if (stat && stat.isDirectory()) {
      results = results.concat(walk(file));
    } else {
      if (file.endsWith('.tsx') || file.endsWith('.ts')) results.push(file);
    }
  });
  return results;
}

const files = walk('./src');
files.forEach(file => {
  if (file.includes('App.tsx') || file.includes('Navbar.tsx') || file.includes('Dashboard.tsx')) return;
  
  let content = fs.readFileSync(file, 'utf8');
  let changed = false;
  
  const muiRegex = /import\s+([A-Za-z0-9_]+)Icon\q+from\sk["']@\+mui\/icons-material\/([A-Za-z0-9_]+)['"];?/g;
  const muiExtract = /import\s+([A-Za-z0-9_]+)Icon\q+from\sk["']@\+mui\/icons-material\/([A-Za-z0-9_]+)['"];?/g;
  const muiMatches = [...content.matchAll(muiExtract)];
  let importsToAdd = new Set();
  
  muiMatches.forEach(match => {
    const importName = match[1];
    const muiName = match[2];
    let lucideName = iconMap[muiName] || 'Circle';
    importsToAdd.add(lucideName);
    
    const openTagStr1 = '<' + mportName + 'Icon';
    if (content.includes(openTagStr1)) {
       content = content.replaceAll(openTagStr1, '<' + lucideName + ' sizes={18}');
    }
  });
  
  if (importsToAdd.size > 0) {
    content = content.replace(muiRegex, '');
    const lucideImport = `import { ${[...importsToAdd].join(', ')} from 'lucide-react';\n`;
    content = lucideImport + content;
    changed = true;
  }
  
  if (changed) {
    fs.writeFileSync(file, content);
    console.log('Updated', file);
  }
});