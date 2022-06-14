package com.dotcms.security.matrixparams;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import com.dotcms.filters.interceptor.Result;
import com.dotcms.filters.interceptor.WebInterceptor;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;
import com.google.common.annotations.VisibleForTesting;
import io.vavr.Lazy;

/**
 * This web interceptor blocks requests with URIs that contain semicolons
 * @author dotCMS
 *
 */
public class MatrixParametersSecurityWebInterceptor implements WebInterceptor {

    private static final String[] BLOCK_REQUESTS = {
            "/*"};

    public MatrixParametersSecurityWebInterceptor() {

    }


    /**
     * Allow a regex to be set in config that can filter for bad urls in case of future issues
     */
    final Lazy<Optional<Pattern>> forbiddenRegex = Lazy.of(() -> {
        final String pattern = Config.getStringProperty("URI_NORMALIZATION_FORBIDDEN_REGEX", null);
        return (pattern != null) ? Optional.of(Pattern.compile(pattern)) : Optional.empty();
    });


    /**
     * Default list of disallowed char sequences
     */
    final String[] DISALLOWED_URI_DEFAULT = new String[] {
            ";",
            "..",
            "//",
            "/./",
            "\\",
            "?",
            "=",
            "%3B", // encoded semi-colon
            "%2E", // encoded period '.'
            "%2F", // encoded forward slash '/'
            "%5C", // encoded back slash '\'
            "%3F", // encoded questionmark
            "%3D", // encoded equals
            "%00", // encoded null
            "\0", // null
            "\r", // carriage return
            "\n", // line feed
            "\f"  // form feed
    };



    final Lazy<String[]> forbiddenURIStrings =
                    Lazy.of(() -> Config.getStringArrayProperty("URI_NORMALIZATION_FORBIDDEN_STRINGS", DISALLOWED_URI_DEFAULT));

    @Override
    public String[] getFilters() {
        return BLOCK_REQUESTS;
    }

    @Override
    public Result intercept(final HttpServletRequest request, final HttpServletResponse response) throws IOException {

        try {
            NormalizationRequestWrapper requestWrapper = new NormalizationRequestWrapper((HttpServletRequest) request);
            return new Result.Builder().wrap(requestWrapper).build();

        } catch (IllegalArgumentException iae) {
            Logger.warnAndDebug(getClass(), "Invalid URI from:" + request.getRemoteAddr() + ", returning a 404", iae);
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        return Result.SKIP_NO_CHAIN;
    }

    /**
     * Normalization is the process of removing unnecessary "." and ".." segments from the path
     * component of a hierarchical URI. 1. Each "." segment is simply removed. 2. A ".." segment is
     * removed only if it is preceded by a non-".." segment. 3. Normalization has no effect upon opaque
     * URIs. (mailto:a@b.com)
     */
    public class NormalizationRequestWrapper extends HttpServletRequestWrapper {

        @VisibleForTesting
        boolean hasError = false;
        String myUri;

        public NormalizationRequestWrapper(HttpServletRequest request) {
            super(request);
            myUri = normalizeRequestUri();
        }

        @Override
        public String getRequestURI() {
            return myUri;
        }

        private String normalizeRequestUri() {

            final String originalUri = super.getRequestURI();


            if (forbiddenRegex.get().isPresent() && forbiddenRegex.get().get().matcher(originalUri).find()) {
                hasError = true;
                throw new IllegalArgumentException("Invalid URI passed:" + originalUri);
            }

            if (containsNastyChar(originalUri)) {
                hasError = true;
                throw new IllegalArgumentException("Invalid URI passed:" + originalUri);
            }
            String newNormal = originalUri.replace("+", "%20").replace(" ", "%20");

            return newNormal.startsWith("/") ? newNormal : "/" + newNormal;

        }

        boolean containsNastyChar(final String newNormal) {
            for (String reserved : forbiddenURIStrings.get()) {
                if (newNormal.contains(reserved)) {
                    return true;
                }
            }
            return false;
        }



        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            myUri = path;
            return super.getRequestDispatcher(path);
        }
    }


}
