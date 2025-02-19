/*
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Force SSL handler. Check for non-HTTPs request and force client to use HTTPs by redirecting the
 * call to the HTTPs version.
 *
 * <p>If you run behind a reverse proxy that has been configured to send the X-Forwarded-* header,
 * please consider to set {@link Router#setTrustProxy(boolean)} option.
 *
 * @author edgar
 */
public class SSLHandler implements Route.Before {
  private static final int SECURE_PORT = 443;
  private final String host;
  private final int port;

  /**
   * Creates a SSLHandler and redirect non-HTTPS request to the given host and port.
   *
   * <p>If you run behind a reverse proxy that has been configured to send the X-Forwarded-* header,
   * please consider to set {@link Router#setTrustProxy(boolean)} option.
   *
   * @param host Host to redirect.
   * @param port HTTP port.
   */
  public SSLHandler(@NonNull String host, int port) {
    this.host = host;
    this.port = port;
  }

  /**
   * Creates a SSLHandler and redirect non-HTTPS request to the given host.
   *
   * <p>If you run behind a reverse proxy that has been configured to send the X-Forwarded-* header,
   * please consider to set {@link Router#setTrustProxy(boolean)} option.
   *
   * @param host Host to redirect.
   */
  public SSLHandler(@NonNull String host) {
    this(host, SECURE_PORT);
  }

  /**
   * Creates a SSLHandler and redirect non-HTTPs requests to the HTTPS version of this call. Host is
   * recreated from <code>Host</code> header or <code>X-Forwarded-Host</code>.
   *
   * <p>If you run behind a reverse proxy that has been configured to send the X-Forwarded-* header,
   * please consider to set {@link Router#setTrustProxy(boolean)} option.
   *
   * @param port HTTPS port.
   */
  public SSLHandler(int port) {
    this.host = null;
    this.port = port;
  }

  /**
   * Creates a SSLHandler and redirect non-HTTPs requests to the HTTPS version of this call. Host is
   * recreated from <code>Host</code> header.
   *
   * <p>If you run behind a reverse proxy that has been configured to send the X-Forwarded-* header,
   * please consider to set {@link Router#setTrustProxy(boolean)} option.
   */
  public SSLHandler() {
    this(SECURE_PORT);
  }

  @Override
  public void apply(@NonNull Context ctx) {
    if (!ctx.isSecure()) {
      String host;
      if (this.host == null) {
        String hostAndPort = ctx.getHostAndPort();
        int i = hostAndPort.lastIndexOf(':');
        host = i > 0 ? hostAndPort.substring(0, i) : hostAndPort;
      } else {
        host = this.host;
      }
      StringBuilder buff = new StringBuilder("https://");
      buff.append(host);

      if (host.equals("localhost")) {
        int securePort = ctx.getRouter().getServerOptions().getSecurePort();
        buff.append(":").append(securePort);
      } else {
        if (port > 0 && port != SECURE_PORT) {
          buff.append(":").append(port);
        }
      }
      buff.append(ctx.getRequestPath());
      buff.append(ctx.queryString());
      ctx.sendRedirect(buff.toString());
    }
  }
}
