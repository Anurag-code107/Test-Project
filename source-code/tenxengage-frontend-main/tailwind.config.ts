import type { Config } from "tailwindcss";
import tailwindcssAnimate from "tailwindcss-animate";

export default {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
  	container: {
  		center: true,
  		padding: '2rem',
  		screens: {
  			'2xl': '1400px'
  		}
  	},
  	extend: {
  		colors: {
  			border: 'hsl(var(--border))',
  			input: 'hsl(var(--input))',
  			ring: 'hsl(var(--ring))',
  			background: 'hsl(var(--background))',
  			foreground: 'hsl(var(--foreground))',
  			primary: {
  				DEFAULT: 'hsl(var(--primary))',
  				foreground: 'hsl(var(--primary-foreground))',
  				light: 'hsl(var(--primary-light))'
  			},
  			secondary: {
  				DEFAULT: 'hsl(var(--secondary))',
  				foreground: 'hsl(var(--secondary-foreground))'
  			},
  			destructive: {
  				DEFAULT: 'hsl(var(--destructive))',
  				foreground: 'hsl(var(--destructive-foreground))'
  			},
  			muted: {
  				DEFAULT: 'hsl(var(--muted))',
  				foreground: 'hsl(var(--muted-foreground))'
  			},
  			accent: {
  				DEFAULT: 'hsl(var(--accent))',
  				foreground: 'hsl(var(--accent-foreground))'
  			},
  			popover: {
  				DEFAULT: 'hsl(var(--popover))',
  				foreground: 'hsl(var(--popover-foreground))'
  			},
  			card: {
  				DEFAULT: 'hsl(var(--card))',
  				foreground: 'hsl(var(--card-foreground))'
  			},
  			success: 'hsl(var(--success))',
  			warning: 'hsl(var(--warning))'
  		},
  		borderRadius: {
  			lg: 'var(--radius)',
  			md: 'calc(var(--radius) - 2px)',
  			sm: 'calc(var(--radius) - 4px)'
  		},
  		keyframes: {
  			'accordion-down': {
  				from: {
  					height: '0'
  				},
  				to: {
  					height: 'var(--radix-accordion-content-height)'
  				}
  			},
  			'accordion-up': {
  				from: {
  					height: 'var(--radix-accordion-content-height)'
  				},
  				to: {
  					height: '0'
  				}
  			},
  			drift: {
  				'0%, 100%': {
  					transform: 'translate(0, 0) scale(1)'
  				},
  				'25%': {
  					transform: 'translate(5%, 15%) scale(1.05)'
  				},
  				'50%': {
  					transform: 'translate(-5%, 5%) scale(0.95)'
  				},
  				'75%': {
  					transform: 'translate(3%, -10%) scale(1.02)'
  				}
  			},
  			'flip-out': {
  				'0%': { transform: 'rotateY(0deg)', opacity: '1' },
  				'100%': { transform: 'rotateY(90deg)', opacity: '0' },
  			},
  			'flip-in': {
  				'0%': { transform: 'rotateY(-90deg)', opacity: '0' },
  				'100%': { transform: 'rotateY(0deg)', opacity: '1' },
  			},
  			'flip-out-reverse': {
  				'0%': { transform: 'rotateY(0deg)', opacity: '1' },
  				'100%': { transform: 'rotateY(-90deg)', opacity: '0' },
  			},
  			'flip-in-reverse': {
  				'0%': { transform: 'rotateY(90deg)', opacity: '0' },
  				'100%': { transform: 'rotateY(0deg)', opacity: '1' },
  			},
  			'fade-in': {
  				'0%': { opacity: '0', transform: 'translateY(10px)' },
  				'100%': { opacity: '1', transform: 'translateY(0)' },
  			},
  			'route-in': {
  				'0%': { opacity: '0', transform: 'translateY(14px)' },
  				'100%': { opacity: '1', transform: 'translateY(0)' },
  			},
  			'tab-content-in': {
  				'0%': { opacity: '0', transform: 'translateY(8px) scale(0.995)' },
  				'100%': { opacity: '1', transform: 'translateY(0) scale(1)' },
  			}
  		},
  		animation: {
  			'accordion-down': 'accordion-down 0.2s ease-out',
  			'accordion-up': 'accordion-up 0.2s ease-out',
  			drift: 'drift 18s ease-in-out infinite',
  			'flip-out': 'flip-out 0.25s ease-in forwards',
  			'flip-in': 'flip-in 0.25s ease-out forwards',
  			'flip-out-reverse': 'flip-out-reverse 0.25s ease-in forwards',
  			'flip-in-reverse': 'flip-in-reverse 0.25s ease-out forwards',
  			'fade-in': 'fade-in 0.3s ease-out',
  			'route-in': 'route-in 0.6s cubic-bezier(0.16, 1, 0.3, 1) both',
  			'tab-content-in': 'tab-content-in 0.3s cubic-bezier(0.16, 1, 0.3, 1) both'
  		}
  	}
  },
  plugins: [tailwindcssAnimate],
} satisfies Config;
