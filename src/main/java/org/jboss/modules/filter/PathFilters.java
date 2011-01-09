/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.modules.filter;

import java.util.Collection;
import java.util.Set;

/**
 * Static factory methods for path filter types.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class PathFilters {
    private PathFilters() {}

    /**
     * Get a path filter which returns {@code true} if all of the given filters return {@code true}.
     *
     * @param filters the filters
     * @return the "all" filter
     */
    public static PathFilter all(PathFilter... filters) {
        return new AggregatePathFilter(false, filters);
    }

    /**
     * Get a path filter which returns {@code true} if all of the given filters return {@code true}.
     *
     * @param filters the filters
     * @return the "all" filter
     */
    public static PathFilter all(Collection<PathFilter> filters) {
        return all(filters.toArray(new PathFilter[filters.size()]));
    }

    /**
     * Get a path filter which returns {@code true} if any of the given filters return {@code true}.
     *
     * @param filters the filters
     * @return the "any" filter
     */
    public static PathFilter any(PathFilter... filters) {
        return new AggregatePathFilter(true, filters);
    }

    /**
     * Get a path filter which returns {@code true} if any of the given filters return {@code true}.
     *
     * @param filters the filters
     * @return the "any" filter
     */
    public static PathFilter any(Collection<PathFilter> filters) {
        return any(filters.toArray(new PathFilter[filters.size()]));
    }

    /**
     * Get a path filter which is {@code true} when the given filter is {@code false}, and vice-versa.
     *
     * @param filter the filter
     * @return the inverting filter
     */
    public static PathFilter not(PathFilter filter) {
        return new InvertingPathFilter(filter);
    }

    /**
     * Get a path filter which matches a glob.  The given glob is a path separated
     * by "{@code /}" characters, which may include the special "{@code *}" and "{@code **}" segment strings
     * which match any directory and any number of nested directories, respectively.
     *
     * @param glob the glob
     * @return a filter which returns {@code true} if the glob matches
     */
    public static PathFilter match(String glob) {
        return new GlobPathFilter(glob);
    }

    /**
     * Get a path filter which matches an exact path name.
     *
     * @param path the path name
     * @return a filter which returns {@code true} if the path name is an exact match
     */
    public static PathFilter is(String path) {
        return new EqualsPathFilter(path);
    }

    /**
     * Get a path filter which matches any path which is a child of the given path name (not including the
     * path name itself).
     *
     * @param path the path name
     * @return a filter which returns {@code true} if the path name is a child of the given path
     */
    public static PathFilter isChildOf(String path) {
        return new ChildPathFilter(path);
    }

    /**
     * Get a builder for a multiple-path filter.  Such a filter contains multiple filters, each associated
     * with a flag which indicates that matching paths should be included or excluded.
     *
     * @param defaultValue the value to return if none of the nested filters match
     * @return the builder
     */
    public static MultiplePathFilterBuilder multiplePathFilterBuilder(boolean defaultValue) {
        return new MultiplePathFilterBuilder(defaultValue);
    }

    /**
     * Get a filter which always returns {@code true}.
     *
     * @return the accept-all filter
     */
    public static PathFilter acceptAll() {
        return BooleanPathFilter.TRUE;
    }

    /**
     * Attempt to quickly determine if this filter will always return true, accepting all paths
     *
     * @param filter the filter
     * @return {@code true} if this filter will always return true. {@code false} indicates that we are unsure.
     */
    public static boolean willAcceptAll(PathFilter filter) {
        return (filter == BooleanPathFilter.TRUE);
    }

    /**
     * Get a filter which always returns {@code false}.
     *
     * @return the reject-all filter
     */
    public static PathFilter rejectAll() {
        return BooleanPathFilter.FALSE;
    }

    /**
     * Attempt to quickly determine if this filter will always return false, rejecting all paths.
     *
     * @param filter the filter
     * @return {@code true} if this filter will always return false. {@code false} indicates that we are unsure.
     */
    public static boolean willRejectAll(PathFilter filter) {
        return (filter == BooleanPathFilter.FALSE);
    }

    /**
     * Get a filter which returns {@code true} if the tested path is contained within the given set.
     * Each member of the set is a path separated by "{@code /}" characters; {@code null}s are disallowed.
     *
     * @param paths the path set
     * @return the filter
     */
    public static PathFilter in(Set<String> paths) {
        return new SetPathFilter(paths);
    }

    private static final PathFilter defaultImportFilter;
    private static final PathFilter defaultImportFilterWithServices;
    private static final PathFilter metaInfFilter;
    private static final PathFilter metaInfSubdirectoriesFilter;
    private static final PathFilter metaInfServicesFilter;

    static {
        final PathFilter metaInfChildren = PathFilters.isChildOf("META-INF");
        final PathFilter metaInf = PathFilters.is("META-INF");
        final PathFilter metaInfServices = PathFilters.is("META-INF/services");

        metaInfFilter = metaInf;
        metaInfSubdirectoriesFilter = metaInfChildren;
        metaInfServicesFilter = metaInfServices;

        final MultiplePathFilterBuilder builder = PathFilters.multiplePathFilterBuilder(true);
        builder.addFilter(metaInfChildren, false);
        builder.addFilter(metaInf, false);
        defaultImportFilter = builder.create();

        final MultiplePathFilterBuilder builder2 = PathFilters.multiplePathFilterBuilder(true);
        builder2.addFilter(metaInfServices, true);
        builder2.addFilter(metaInfChildren, false);
        builder2.addFilter(metaInf, false);
        defaultImportFilterWithServices = builder2.create();
    }

    /**
     * Get the default import path filter, which excludes all of {@code META-INF} and its subdirectories.
     *
     * @return the default import path filter
     */
    public static PathFilter getDefaultImportFilter() {
        return defaultImportFilter;
    }

    /**
     * Get the default import-with-services path filter which excludes all of {@code META-INF} and its subdirectories,
     * with the exception of {@code META-INF/services}.
     *
     * @return the default import-with-services path filter
     */
    public static PathFilter getDefaultImportFilterWithServices() {
        return defaultImportFilterWithServices;
    }

    /**
     * Get a filter which matches the path {@code "META-INF"}.
     *
     * @return the filter
     */
    public static PathFilter getMetaInfFilter() {
        return metaInfFilter;
    }

    /**
     * Get a filter which matches any subdirectory of the path {@code "META-INF"}.
     *
     * @return the filter
     */
    public static PathFilter getMetaInfSubdirectoriesFilter() {
        return metaInfSubdirectoriesFilter;
    }

    /**
     * Get a filter which matches the path {@code "META-INF/services"}.
     *
     * @return the filter
     */
    public static PathFilter getMetaInfServicesFilter() {
        return metaInfServicesFilter;
    }
}
