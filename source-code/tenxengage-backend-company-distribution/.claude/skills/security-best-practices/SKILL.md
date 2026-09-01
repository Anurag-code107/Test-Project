# Security Best Practices for Claude Code

> Production-ready security practices for AI-generated code in enterprise environments

## ⚠️ The Security Reality with AI-Generated Code

**Critical Understanding**: AI-generated code consistently introduces security vulnerabilities at approximately 2% of all defects, often at BLOCKER/CRITICAL severity levels. This requires systematic security validation, not optional security checks.

### Common AI Security Vulnerabilities

#### High-Frequency Issues
- **Hard-coded credentials** (passwords, API keys embedded in source)
- **Path traversal flaws** (inadequate input validation)
- **Cryptography misconfiguration** (deprecated algorithms, weak keys)
- **Injection vulnerabilities** (SQL injection, XSS susceptibility)
- **Insufficient input validation** (missing or inadequate sanitization)
- **Insecure authentication patterns** (weak session management)

## 🛡️ Proactive Security Integration

### Security-First Development Process

#### Pre-Implementation Security Planning
```bash
# Security requirements definition
> "Before implementing [FEATURE], define comprehensive security requirements:

**Threat Model Analysis:**
- What assets does this feature protect or access?
- Who are the potential threat actors?
- What are the possible attack vectors?
- What is the impact of a successful attack?

**Security Requirements:**
- Authentication and authorization needs
- Input validation and sanitization requirements
- Data protection and encryption needs
- Logging and monitoring requirements
- Rate limiting and abuse prevention

**Compliance Considerations:**
- GDPR, CCPA, or other privacy regulations
- Industry-specific compliance (HIPAA, PCI-DSS, SOX)
- Company security policies and standards
- Third-party security requirements

Generate specific security requirements that will guide implementation."
```

#### Security-Aware Implementation
```bash
# Secure implementation prompt pattern
> "Implement [FEATURE] following security-first principles:

**Input Security:**
- Validate ALL user inputs using whitelist validation
- Sanitize inputs to prevent XSS and injection attacks
- Implement proper parameter validation and type checking
- Use prepared statements for database queries

**Authentication & Authorization:**
- Implement proper session management
- Use secure token generation and validation
- Apply principle of least privilege
- Check authorization at every access point

**Data Protection:**
- Encrypt sensitive data at rest and in transit
- Use secure key management practices
- Implement proper data anonymization/pseudonymization
- Follow data retention and deletion policies

**Error Handling:**
- Never expose sensitive information in error messages
- Log security events without exposing sensitive data
- Implement proper error boundaries and fallbacks
- Use generic error messages for user-facing responses

**Generate implementation with security built-in, not bolted-on.**"
```

### OWASP Top 10 Integration

#### Systematic OWASP Compliance
```bash
# OWASP Top 10 security review
> "Review this implementation against OWASP Top 10 vulnerabilities:

**A01 - Broken Access Control:**
- Are authorization checks present at every access point?
- Is the principle of least privilege enforced?
- Are there any privilege escalation vulnerabilities?
- Is access control consistently implemented?

**A02 - Cryptographic Failures:**
- Is sensitive data encrypted in transit and at rest?
- Are strong, current cryptographic algorithms used?
- Is key management implemented securely?
- Are there any hard-coded cryptographic secrets?

**A03 - Injection:**
- Are all user inputs validated and sanitized?
- Are parameterized queries used for database access?
- Is there protection against NoSQL injection?
- Are system commands properly sanitized?

**A04 - Insecure Design:**
- Was security considered in the design phase?
- Are threat models and security requirements defined?
- Is the architecture defensible against known threats?
- Are security controls integrated into the design?

**A05 - Security Misconfiguration:**
- Are default passwords and configurations changed?
- Are unnecessary features and services disabled?
- Are security headers properly configured?
- Is the system hardened according to security baselines?

**A06 - Vulnerable and Outdated Components:**
- Are all dependencies current and patched?
- Are vulnerability scans performed regularly?
- Is there a process for security updates?
- Are component licenses and security advisories monitored?

**A07 - Identification and Authentication Failures:**
- Is multi-factor authentication implemented where needed?
- Are passwords properly hashed and salted?
- Is session management secure and robust?
- Are brute force attacks properly mitigated?

**A08 - Software and Data Integrity Failures:**
- Is code integrity verified during deployment?
- Are updates delivered over secure channels?
- Is there protection against supply chain attacks?
- Are digital signatures used for critical updates?

**A09 - Security Logging and Monitoring Failures:**
- Are security events properly logged?
- Is monitoring in place for attack detection?
- Are logs protected from tampering?
- Is there an incident response process?

**A10 - Server-Side Request Forgery (SSRF):**
- Are outbound requests properly validated?
- Is there protection against internal service access?
- Are URL validation and sanitization implemented?
- Is network segmentation in place?

Provide specific remediation steps for any issues identified."
```

## 🔐 Secure Coding Patterns

### Authentication and Authorization

#### Secure Authentication Implementation
```bash
# Secure authentication system prompt
> "Implement secure user authentication following these patterns:

**Password Security:**
- Use bcrypt with minimum 12 salt rounds
- Implement password strength requirements (length, complexity)
- Prevent password reuse (store hash history)
- Implement secure password reset with time-limited tokens

**Token Management:**
- Use cryptographically secure random token generation
- Implement proper token expiration (short-lived access tokens)
- Use secure token storage (HttpOnly cookies or secure storage)
- Implement token refresh mechanisms with rotation

**Session Security:**
- Generate new session IDs after authentication
- Implement secure session storage and management
- Use proper session timeout policies
- Implement concurrent session limits

**Multi-Factor Authentication:**
- Support TOTP (Time-based One-Time Passwords)
- Implement backup codes for account recovery
- Use secure QR code generation for setup
- Provide clear user instructions for MFA setup

**Example Implementation Pattern:**
```typescript
// Secure authentication service
export class AuthService {
  async authenticateUser(email: string, password: string): Promise<AuthResult> {
    // Input validation
    if (!this.isValidEmail(email) || !this.isValidPassword(password)) {
      await this.simulateHashingDelay(); // Prevent timing attacks
      return { success: false, error: 'Invalid credentials' };
    }
    
    // Rate limiting check
    if (await this.isRateLimited(email)) {
      return { success: false, error: 'Too many attempts' };
    }
    
    // Secure password verification
    const user = await this.findUserByEmail(email);
    if (!user || !await bcrypt.compare(password, user.hashedPassword)) {
      await this.logFailedAttempt(email);
      return { success: false, error: 'Invalid credentials' };
    }
    
    // Generate secure tokens
    const accessToken = await this.generateAccessToken(user.id);
    const refreshToken = await this.generateRefreshToken(user.id);
    
    await this.logSuccessfulLogin(user.id);
    return { success: true, accessToken, refreshToken };
  }
}
```

Follow this pattern for all authentication implementations."
```

#### Authorization Pattern Implementation
```bash
# Secure authorization patterns
> "Implement role-based authorization with these security patterns:

**Authorization Principles:**
- Check permissions at every access point (fail-secure)
- Use role-based access control (RBAC) with minimal privileges
- Implement resource-level permissions where needed
- Never trust client-side authorization checks

**Implementation Pattern:**
```typescript
// Authorization middleware
export const requirePermission = (permission: string) => {
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
      // Verify token and get user
      const user = await this.verifyToken(req.headers.authorization);
      if (!user) {
        return res.status(401).json({ error: 'Unauthorized' });
      }
      
      // Check permission
      const hasPermission = await this.checkUserPermission(user.id, permission);
      if (!hasPermission) {
        await this.logUnauthorizedAccess(user.id, permission);
        return res.status(403).json({ error: 'Forbidden' });
      }
      
      req.user = user;
      next();
    } catch (error) {
      await this.logAuthorizationError(error);
      return res.status(401).json({ error: 'Invalid token' });
    }
  };
};

// Usage in routes
router.get('/admin/users', requirePermission('users.read'), getUsersHandler);
router.post('/admin/users', requirePermission('users.create'), createUserHandler);
```

**Resource-Level Authorization:**
```typescript
// Check resource ownership
export const requireResourceOwnership = (resourceType: string) => {
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    const resourceId = req.params.id;
    const isOwner = await this.checkResourceOwnership(req.user.id, resourceType, resourceId);
    
    if (!isOwner) {
      await this.logUnauthorizedResourceAccess(req.user.id, resourceType, resourceId);
      return res.status(403).json({ error: 'Access denied' });
    }
    
    next();
  };
};
```

Apply these patterns consistently across all protected endpoints."
```

### Input Validation and Sanitization

#### Comprehensive Input Security
```bash
# Secure input handling implementation
> "Implement comprehensive input validation and sanitization:

**Input Validation Strategy:**
- Validate all inputs on the server-side (never trust client validation)
- Use whitelist validation (allow known good) over blacklist (block known bad)
- Validate data type, format, length, and range
- Use schema validation libraries (Joi, Yup, Zod) for complex validation

**Implementation Pattern:**
```typescript
import { z } from 'zod';

// Schema-based validation
const UserCreateSchema = z.object({
  email: z.string()
    .email('Invalid email format')
    .max(254, 'Email too long')
    .transform(email => email.toLowerCase().trim()),
  
  password: z.string()
    .min(12, 'Password must be at least 12 characters')
    .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/, 
           'Password must contain uppercase, lowercase, number, and special character'),
  
  name: z.string()
    .min(2, 'Name must be at least 2 characters')
    .max(50, 'Name must be less than 50 characters')
    .regex(/^[a-zA-Z\s'-]+$/, 'Name contains invalid characters')
    .transform(name => name.trim()),
  
  age: z.number()
    .int('Age must be an integer')
    .min(13, 'Must be at least 13 years old')
    .max(120, 'Invalid age'),
});

// Validation middleware
export const validateInput = (schema: z.ZodSchema) => {
  return (req: Request, res: Response, next: NextFunction) => {
    try {
      const validated = schema.parse(req.body);
      req.body = validated; // Use sanitized data
      next();
    } catch (error) {
      if (error instanceof z.ZodError) {
        return res.status(400).json({
          error: 'Validation failed',
          details: error.errors.map(e => ({ field: e.path, message: e.message }))
        });
      }
      next(error);
    }
  };
};

// Usage
router.post('/users', validateInput(UserCreateSchema), createUserHandler);
```

**SQL Injection Prevention:**
```typescript
// Always use parameterized queries
const getUserById = async (id: string) => {
  // ✅ CORRECT: Parameterized query
  const query = 'SELECT * FROM users WHERE id = $1';
  const result = await db.query(query, [id]);
  return result.rows[0];
  
  // ❌ NEVER DO THIS: String concatenation
  // const query = `SELECT * FROM users WHERE id = '${id}'`;
};

// For complex queries, use query builders
const getFilteredUsers = async (filters: UserFilters) => {
  let query = db('users').select('*');
  
  if (filters.email) {
    query = query.where('email', 'ilike', `%${filters.email}%`);
  }
  
  if (filters.status) {
    query = query.where('status', filters.status);
  }
  
  return await query;
};
```

**XSS Prevention:**
```typescript
import DOMPurify from 'dompurify';

// Sanitize HTML content
const sanitizeHtml = (html: string): string => {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'ul', 'ol', 'li'],
    ALLOWED_ATTR: []
  });
};

// Output encoding for different contexts
const encodeForHtml = (text: string): string => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
};
```

Use these patterns for all user input processing."
```

## 🔍 Security Testing and Validation

### Automated Security Testing

#### Security Scanning Integration
```bash
# Comprehensive security scanning setup
> "Set up automated security scanning for this project:

**Static Analysis Security Testing (SAST):**
- Semgrep for multi-language security pattern detection
- ESLint security plugins for JavaScript/TypeScript
- Bandit for Python security analysis
- SonarQube for comprehensive code quality and security

**Dynamic Analysis Security Testing (DAST):**
- OWASP ZAP for web application security testing
- SQLmap for SQL injection testing
- Custom security test suites for API testing

**Dependency Scanning:**
- npm audit for Node.js dependencies
- Safety for Python dependencies  
- Dependabot or Renovate for automated updates
- License compliance checking

**Configuration Example:**
```yaml
# .github/workflows/security.yml
name: Security Scanning

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Run Semgrep
        uses: returntocorp/semgrep-action@v1
        with:
          config: auto
          
      - name: Run npm audit
        run: npm audit --audit-level=moderate
        
      - name: Run OWASP ZAP Baseline Scan
        uses: zaproxy/action-baseline@v0.7.0
        with:
          target: 'http://localhost:3000'
          
      - name: Upload security scan results
        uses: github/codeql-action/upload-sarif@v2
        if: always()
        with:
          sarif_file: results.sarif
```

Set up these scans to run automatically on every commit and deployment."
```

#### Penetration Testing Automation
```bash
# Automated penetration testing integration
> "Create automated security testing for this application:

**API Security Testing:**
```javascript
// Security test suite using Jest and Supertest
describe('API Security Tests', () => {
  describe('Authentication Bypass Attempts', () => {
    test('should reject requests without authentication', async () => {
      const response = await request(app)
        .get('/api/users')
        .expect(401);
      
      expect(response.body.error).toBe('Unauthorized');
    });
    
    test('should reject invalid JWT tokens', async () => {
      const response = await request(app)
        .get('/api/users')
        .set('Authorization', 'Bearer invalid-token')
        .expect(401);
    });
    
    test('should reject expired JWT tokens', async () => {
      const expiredToken = jwt.sign({ userId: 1 }, process.env.JWT_SECRET, { expiresIn: '-1h' });
      
      const response = await request(app)
        .get('/api/users')
        .set('Authorization', `Bearer ${expiredToken}`)
        .expect(401);
    });
  });
  
  describe('SQL Injection Attempts', () => {
    test('should prevent SQL injection in user search', async () => {
      const maliciousInput = "'; DROP TABLE users; --";
      
      const response = await request(app)
        .get('/api/users/search')
        .query({ name: maliciousInput })
        .set('Authorization', `Bearer ${validToken}`)
        .expect(400);
        
      // Verify database is still intact
      const userCount = await db.query('SELECT COUNT(*) FROM users');
      expect(userCount.rows[0].count).toBeGreaterThan(0);
    });
  });
  
  describe('XSS Prevention', () => {
    test('should sanitize user input in profile updates', async () => {
      const xssPayload = '<script>alert("xss")</script>';
      
      const response = await request(app)
        .put('/api/users/profile')
        .send({ bio: xssPayload })
        .set('Authorization', `Bearer ${validToken}`)
        .expect(200);
        
      expect(response.body.bio).not.toContain('<script>');
    });
  });
  
  describe('Rate Limiting', () => {
    test('should enforce rate limits on login attempts', async () => {
      const requests = Array(10).fill().map(() =>
        request(app)
          .post('/api/auth/login')
          .send({ email: 'test@example.com', password: 'wrongpassword' })
      );
      
      const responses = await Promise.all(requests);
      const rateLimitedResponses = responses.filter(r => r.status === 429);
      
      expect(rateLimitedResponses.length).toBeGreaterThan(0);
    });
  });
});
```

**Load Testing with Security Focus:**
```javascript
// Artillery.js security-focused load testing
module.exports = {
  config: {
    target: 'http://localhost:3000',
    phases: [
      { duration: 60, arrivalRate: 10 }, // Warm up
      { duration: 120, arrivalRate: 50 }, // Load testing
      { duration: 60, arrivalRate: 100 }, // Stress testing
    ],
  },
  scenarios: [
    {
      name: 'Authentication Security Under Load',
      weight: 40,
      flow: [
        { post: { url: '/api/auth/login', json: { email: 'test@example.com', password: 'testpassword' } } },
        { get: { url: '/api/users/profile', headers: { Authorization: 'Bearer {{ token }}' } } },
      ],
    },
    {
      name: 'Malicious Request Patterns',
      weight: 20,
      flow: [
        { get: { url: '/api/users?id=1 OR 1=1' } }, // SQL injection attempt
        { post: { url: '/api/users', json: { name: '<script>alert(1)</script>' } } }, // XSS attempt
      ],
    },
  ],
};
```

Integrate these tests into your CI/CD pipeline for continuous security validation."
```

## 🚨 Incident Response and Security Monitoring

### Security Event Logging

#### Comprehensive Security Logging
```bash
# Security event logging implementation
> "Implement comprehensive security event logging:

**Security Events to Log:**
- Authentication attempts (success/failure)
- Authorization failures
- Suspicious request patterns
- Data access and modifications
- Configuration changes
- Error conditions that might indicate attacks

**Logging Implementation:**
```typescript
import winston from 'winston';

// Security-focused logger configuration
const securityLogger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.errors({ stack: true }),
    winston.format.json()
  ),
  defaultMeta: { service: 'security' },
  transports: [
    new winston.transports.File({ filename: 'logs/security.log' }),
    new winston.transports.Console({ format: winston.format.simple() })
  ],
});

// Security event logging functions
export const logSecurityEvent = (eventType: string, details: any, request?: Request) => {
  const logData = {
    eventType,
    timestamp: new Date().toISOString(),
    userAgent: request?.headers['user-agent'],
    ipAddress: request?.ip,
    userId: request?.user?.id,
    sessionId: request?.sessionID,
    ...details
  };
  
  securityLogger.info('Security Event', logData);
};

// Usage examples
export const logFailedLogin = (email: string, ipAddress: string) => {
  logSecurityEvent('FAILED_LOGIN', {
    email,
    ipAddress,
    severity: 'MEDIUM'
  });
};

export const logUnauthorizedAccess = (userId: string, resource: string, action: string) => {
  logSecurityEvent('UNAUTHORIZED_ACCESS', {
    userId,
    resource,
    action,
    severity: 'HIGH'
  });
};

export const logSuspiciousActivity = (pattern: string, details: any) => {
  logSecurityEvent('SUSPICIOUS_ACTIVITY', {
    pattern,
    details,
    severity: 'HIGH'
  });
};
```

**Log Analysis and Alerting:**
```typescript
// Real-time security monitoring
export class SecurityMonitor {
  private static instance: SecurityMonitor;
  private failedAttempts: Map<string, number> = new Map();
  
  async analyzeSecurityEvent(event: SecurityEvent) {
    switch (event.eventType) {
      case 'FAILED_LOGIN':
        await this.handleFailedLogin(event);
        break;
      case 'UNAUTHORIZED_ACCESS':
        await this.handleUnauthorizedAccess(event);
        break;
      case 'SUSPICIOUS_ACTIVITY':
        await this.handleSuspiciousActivity(event);
        break;
    }
  }
  
  private async handleFailedLogin(event: SecurityEvent) {
    const key = event.ipAddress || event.email;
    const attempts = (this.failedAttempts.get(key) || 0) + 1;
    this.failedAttempts.set(key, attempts);
    
    if (attempts >= 5) {
      await this.sendSecurityAlert({
        type: 'BRUTE_FORCE_ATTACK',
        target: key,
        attempts,
        severity: 'HIGH'
      });
      
      // Implement IP blocking or account lockout
      await this.blockIpAddress(event.ipAddress);
    }
  }
  
  private async sendSecurityAlert(alert: SecurityAlert) {
    // Send to security team
    await this.notifySecurityTeam(alert);
    
    // Log to security information and event management (SIEM) system
    await this.sendToSIEM(alert);
    
    // Trigger automated response if configured
    if (alert.severity === 'CRITICAL') {
      await this.triggerAutomatedResponse(alert);
    }
  }
}
```

Implement this logging across all security-sensitive operations."
```

### Incident Response Procedures

#### Automated Incident Response
```bash
# Security incident response automation
> "Create automated security incident response procedures:

**Incident Response Workflow:**
1. **Detection**: Automated monitoring detects security event
2. **Classification**: Determine severity and type of incident
3. **Containment**: Automatic containment measures if applicable
4. **Notification**: Alert security team and stakeholders
5. **Investigation**: Gather forensic data and analyze impact
6. **Recovery**: Restore systems and implement fixes
7. **Post-Incident**: Review and improve security measures

**Implementation:**
```typescript
export class IncidentResponse {
  async handleSecurityIncident(incident: SecurityIncident) {
    const severity = await this.classifyIncident(incident);
    
    // Automatic containment for high-severity incidents
    if (severity >= SeverityLevel.HIGH) {
      await this.implementContainment(incident);
    }
    
    // Notification based on severity
    await this.notifyStakeholders(incident, severity);
    
    // Start forensic data collection
    await this.collectForensicData(incident);
    
    // Create incident ticket
    await this.createIncidentTicket(incident, severity);
  }
  
  private async implementContainment(incident: SecurityIncident) {
    switch (incident.type) {
      case 'BRUTE_FORCE_ATTACK':
        await this.blockAttackerIP(incident.sourceIP);
        break;
      case 'DATA_BREACH_ATTEMPT':
        await this.revokeCompromisedTokens(incident.affectedTokens);
        break;
      case 'MALWARE_DETECTION':
        await this.quarantineAffectedSystems(incident.affectedSystems);
        break;
    }
  }
  
  private async collectForensicData(incident: SecurityIncident) {
    const forensicData = {
      timestamp: incident.timestamp,
      logs: await this.extractRelevantLogs(incident),
      networkTraffic: await this.captureNetworkData(incident),
      systemState: await this.captureSystemState(incident),
      userSessions: await this.extractUserSessionData(incident)
    };
    
    await this.storeForensicData(incident.id, forensicData);
  }
}
```

**Incident Response Playbooks:**
```yaml
# Security incident playbooks
playbooks:
  brute_force_attack:
    triggers:
      - failed_login_attempts > 10 within 5 minutes
    actions:
      - block_ip_address
      - notify_security_team
      - increase_monitoring
    
  data_breach_attempt:
    triggers:
      - unauthorized_data_access
      - bulk_data_download
    actions:
      - revoke_user_sessions
      - notify_legal_team
      - preserve_audit_logs
      - contact_data_protection_officer
    
  privilege_escalation:
    triggers:
      - unauthorized_admin_access
      - permission_changes
    actions:
      - revoke_elevated_permissions
      - notify_system_administrators
      - audit_all_recent_changes
```

Configure these automated responses based on your organization's security policies."
```

## 📋 Security Review Checklists

### Pre-Deployment Security Checklist

```markdown
## Security Pre-Deployment Checklist

### Authentication & Authorization
- [ ] All endpoints require appropriate authentication
- [ ] Authorization checks are present at every access point
- [ ] JWT tokens have appropriate expiration times
- [ ] Refresh token rotation is implemented
- [ ] Password hashing uses bcrypt with adequate salt rounds
- [ ] Multi-factor authentication is available for sensitive operations

### Input Validation & Sanitization  
- [ ] All user inputs are validated server-side
- [ ] SQL injection prevention (parameterized queries)
- [ ] XSS prevention (input sanitization, output encoding)
- [ ] File upload security (type validation, size limits)
- [ ] Command injection prevention
- [ ] Path traversal prevention

### Data Protection
- [ ] Sensitive data is encrypted at rest
- [ ] Data transmission uses TLS/SSL
- [ ] Database credentials are not hard-coded
- [ ] API keys and secrets are stored securely
- [ ] Personal data handling complies with privacy regulations
- [ ] Data retention policies are implemented

### Security Headers & Configuration
- [ ] HTTPS is enforced everywhere
- [ ] Security headers are properly configured
- [ ] CORS policies are restrictive and appropriate
- [ ] Rate limiting is implemented on sensitive endpoints
- [ ] Session security is properly configured
- [ ] Error messages don't expose sensitive information

### Monitoring & Logging
- [ ] Security events are logged comprehensively
- [ ] Logs don't contain sensitive information
- [ ] Log integrity protection is in place
- [ ] Security monitoring and alerting is configured
- [ ] Incident response procedures are documented
- [ ] Regular security scans are scheduled

### Dependency & Infrastructure Security
- [ ] All dependencies are up to date
- [ ] Vulnerable dependencies are identified and addressed
- [ ] Infrastructure follows security hardening guidelines
- [ ] Network security controls are in place
- [ ] Backup and recovery procedures include security considerations
- [ ] Security testing is automated in CI/CD pipeline
```

### Code Review Security Checklist

```markdown
## Security Code Review Checklist

### Authentication Code Review
- [ ] Password comparison uses secure comparison functions
- [ ] Token generation uses cryptographically secure random functions
- [ ] Session management follows secure practices
- [ ] Account lockout mechanisms prevent brute force attacks
- [ ] Password reset flows are secure and time-limited

### Authorization Code Review
- [ ] Authorization checks cannot be bypassed
- [ ] Direct object references are protected
- [ ] Privilege escalation is not possible
- [ ] Resource-level permissions are enforced
- [ ] Administrative functions are properly protected

### Input Handling Code Review
- [ ] Input validation is comprehensive and server-side
- [ ] Sanitization is applied consistently
- [ ] File uploads are properly secured
- [ ] Database queries use parameterized statements
- [ ] Regular expressions are not vulnerable to ReDoS

### Cryptography Code Review
- [ ] Strong, current algorithms are used
- [ ] Key generation is cryptographically secure
- [ ] Keys are properly managed and not hard-coded
- [ ] Cryptographic implementations follow standards
- [ ] Random number generation is cryptographically secure

### Error Handling Code Review
- [ ] Errors don't expose sensitive information
- [ ] Error conditions are properly handled
- [ ] Logging includes security-relevant events
- [ ] Stack traces are not exposed to users
- [ ] Fail-secure principles are followed
```

---

> **Critical Reminder**: Security is not optional with AI-generated code. Implement systematic security validation at every stage of development. The convenience of AI code generation must never compromise security posture.