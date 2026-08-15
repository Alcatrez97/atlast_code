import React from 'react';

/**
 * Vi (Vodafone Idea) Brand Logo component.
 * Renders the iconic red badge with bold white "V", "i", and the signature yellow dot.
 */
export const ViLogo = ({ size = 32, className = '' }) => {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 100 100"
      width={size}
      height={size}
      className={className}
      style={{ display: 'block', borderRadius: '6px', flexShrink: 0 }}
      aria-label="Vi Logo"
    >
      {/* Vi Red Background Badge */}
      <rect width="100" height="100" rx="18" fill="#ED1B24" />
      
      {/* "V" Character */}
      <path
        d="M 18 32 L 37.5 74 H 47.5 L 67 32 H 56.5 L 42.5 62.5 L 28.5 32 Z"
        fill="#FFFFFF"
      />
      
      {/* "i" Stem */}
      <rect
        x="73"
        y="44.5"
        width="10"
        height="29.5"
        rx="2"
        fill="#FFFFFF"
      />
      
      {/* Signature Vi Yellow Dot on "i" */}
      <circle
        cx="78"
        cy="34"
        r="5.5"
        fill="#FFD100"
      />
    </svg>
  );
};

export default ViLogo;
