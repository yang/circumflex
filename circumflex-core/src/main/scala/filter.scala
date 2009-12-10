/*
 * Copyright (C) 2009-2010 Boris Okunskiy (http://incarnate.ru)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package ru.circumflex.core

import java.lang.reflect.InvocationTargetException
import java.util.ResourceBundle
import javax.servlet._
import http.{HttpServletResponse, HttpServletRequest}
import org.slf4j.LoggerFactory
import Circumflex._

/**
 * Provides a base class for Circumflex filter implementations.
 */
abstract class AbstractCircumflexFilter extends Filter {

  protected val log = LoggerFactory.getLogger("ru.circumflex.core")

  /**
   * Place your application initialization code here.
   * Does nothing by default.
   */
  def init(filterConfig: FilterConfig) = {}

  /**
   * Place your application shutdown code here.
   * Does nothing by default.
   */
  def destroy = {}

  /**
   * Determines, if a filter should process the request.
   * Used primarily for static resources, where no processing is required.
   * Default behavior is to match requestUri against following regex:
   * <code>(/static/.*)|(.*\\.(gif)|(png)|(jpg)|(jpeg)|(pdf)|(css)|(js))</code>
   * @param req   the request instance
   * @return     <b>true</b> if the request should be processed
   *              <b>false</b> if the processing should be skipped
   */
  def isProcessed(req: HttpServletRequest): Boolean =
    !req.getRequestURI.toLowerCase.matches("(/static/.*)|(.*\\.(gif)|(png)|(jpg)|(jpeg)|(pdf)|(css)|(js)|(swf))")

  /**
   * Instantiates a CircumflexContext object, binds it to current request,
   * consults <code>isProcessed</code>, whether the request should be processed
   * and delegates to high-level equivalent <code>doFilter(CircumflexContext, FilterChain)</code>
   * if necessary.
   */
  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit =
    (req, res) match {
      case (req: HttpServletRequest, res: HttpServletResponse) =>
        if (isProcessed(req)) {
          // Instantiate a context if it does not yet exist and bind it thread-locally.
          if (ctx == null) Circumflex.initContext(req, res, this)
          // Put a Messages helper with current request's locale (if it exists)
          val bundleResource = Circumflex.cfg("cx.messages") match {
            case Some(s: String) => s
            case _ => "Messages"
          }
          try {
            ctx += "msg" -> new Messages(ResourceBundle.getBundle(bundleResource, req.getLocale))
          } catch {
            case _ =>
          }
          // chain a call and make sure the context is destroyed afterwards
          try {
            doFilter(ctx, chain)
          } finally {
            Circumflex.destroyContext()
          }
        } else chain.doFilter(req, res)
      case _ =>
    }

  /**
   * Implementing classes should provide their filtering logic for processed requests.
   * @param ctx    Route Context passed to this filter
   * @param chain  filter chain to delegate calls to if necessary
   */
  def doFilter(ctx: CircumflexContext, chain: FilterChain): Unit

}

/**
 * Configures Circumflex-based web application and serves it's requests.
 * Web application should configure it according to JSR-154 (Java Servlet Specification) via
 * <code>your_webapp_root/WEB-INF/web.xml</code> (also known as Deployment Descriptor).
 *
 * @see ru.circumflex.core.RequestRouter
 */
class CircumflexFilter extends AbstractCircumflexFilter {

  val routerClass: Class[RequestRouter] = Circumflex.cfg("cx.router") match {
    case Some(s: String) => Class.forName(s).asInstanceOf[Class[RequestRouter]]
    case Some(c: Class[RequestRouter]) => c
    case _ => throw new CircumflexException("Could not initialize Request Router; configure 'cx.router' properly.")
  }

  /**
   * Executed when no routes match current request.
   * Default behavior is to send 404 NOT FOUND.
   * You may override it, say, to call <code>chain.doFilter</code> to pass request along the chain.
   * @param ctx    Route Context passed to this filter
   * @param chain  filter chain to delegate calls to if necessary
   */
  def onNoMatch(ctx: CircumflexContext, chain: FilterChain) =
    ErrorResponse(404, "The requested resource does not exist.")(ctx.response)
  //  chain.doFilter(ctx.request, ctx.response)


  /**
   * Executed when router throws an exception.
   * Default behavior is to send the 500 status code to client.
   * @param e      the router's exception
   * @param ctx    Route Context passed to this filter
   * @param chain  filter chain to delegate calls to if necessary
   */
  def onRouterError(e: Throwable, ctx: CircumflexContext, chain: FilterChain) = {
    log.error("Controller threw an exception, see stack trace for details.", e)
    ErrorResponse(500, e.getMessage)(ctx.response)
  }

  /**
   * Instantiates a router that processes current request.
   * @param ctx    Route Context passed to this filter
   * @param chain  filter chain to delegate calls to if necessary
   */
  def doFilter(ctx: CircumflexContext, chain: FilterChain): Unit = {
    log.debug(ctx.request.toString)
    // Set X-Powered-By header
    ctx.response.setHeader("X-Powered-By", "Circumflex v. 0.2")
    // Set character encoding
    ctx.request.setCharacterEncoding("UTF-8")
    try {
      routerClass.getConstructor().newInstance()
      // Request not matched by router
      onNoMatch(ctx, chain)
    } catch {
      case e: InvocationTargetException if e.getCause.isInstanceOf[RouteMatchedException] => {
        // Request matched
        e.getCause.asInstanceOf[RouteMatchedException].response match {
          case Some(response) => response(ctx.response)
          case _ =>
        }
      } case e => {
        onRouterError(e, ctx, chain)
      }
    }
  }

  /**
   * Called when a filter instance is instantiated by Servlet Container.
   * @param cfg    filter configuration
   */
  override def init(cfg: FilterConfig) = {
    log.info("Circumflex v. 0.2")
  }

}
