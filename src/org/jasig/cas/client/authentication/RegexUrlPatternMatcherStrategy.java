/*
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.cas.client.authentication;

import java.util.regex.Pattern;


/**
 * A pattern matcher that looks inside the url to find the pattern,. that
 * is assumed to have been specified via regular expressions syntax.
 * 
 * @author Misagh Moayyed
 * @since 3.3.1
 */
public final class RegexUrlPatternMatcherStrategy implements UrlPatternMatcherStrategy {

    private Pattern pattern;
    
    public boolean matches(final String url) {
        return this.pattern.matcher(url).find();
    }

    public void setPattern(final String pattern) {
        this.pattern = Pattern.compile(pattern);
    }
}
