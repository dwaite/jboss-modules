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

package org.jboss.modules;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.modules.management.ResourceLoaderInfo;

/**
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class JarFileResourceLoader implements ResourceLoader {
    private final JarFile jarFile;
    private final String rootName;
    private final URL rootUrl;

    JarFileResourceLoader(final String rootName, final JarFile jarFile) {
        if (jarFile == null) {
            throw new IllegalArgumentException("jarFile is null");
        }
        if (rootName == null) {
            throw new IllegalArgumentException("rootName is null");
        }
        this.jarFile = jarFile;
        this.rootName = rootName;
        try {
            rootUrl = new URI("jar", "file:" + jarFile.getName() + "!/", null).toURL();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid root file specified", e);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid root file specified", e);
        }
    }

    public String getRootName() {
        return rootName;
    }

    public ClassSpec getClassSpec(final String fileName) throws IOException {
        final ClassSpec spec = new ClassSpec();
        final JarEntry entry = jarFile.getJarEntry(fileName);
        if (entry == null) {
            // no such entry
            return null;
        }
        spec.setCodeSource(new CodeSource(rootUrl, entry.getCodeSigners()));
        final long size = entry.getSize();
        final InputStream is = jarFile.getInputStream(entry);
        try {
            if (size == -1) {
                // size unknown
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final byte[] buf = new byte[16384];
                int res;
                while ((res = is.read(buf)) > 0) {
                    baos.write(buf, 0, res);
                }
                // done
                baos.close();
                is.close();
                spec.setBytes(baos.toByteArray());
                return spec;
            } else if (size <= (long) Integer.MAX_VALUE) {
                final int castSize = (int) size;
                byte[] bytes = new byte[castSize];
                int a = 0, res;
                while ((res = is.read(bytes, a, castSize - a)) > 0) {
                    a += res;
                }
                // done
                is.close();
                spec.setBytes(bytes);
                return spec;
            } else {
                throw new IOException("Resource is too large to be a valid class file");
            }
        } finally {
            safeClose(is);
        }
    }

    private static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }

    public PackageSpec getPackageSpec(final String name) throws IOException {
        final PackageSpec spec = new PackageSpec();
        final Manifest manifest = jarFile.getManifest();
        if (manifest == null) {
            return spec;
        }
        final Attributes mainAttribute = manifest.getMainAttributes();
        final Attributes entryAttribute = manifest.getAttributes(name);
        spec.setSpecTitle(getDefinedAttribute(Attributes.Name.SPECIFICATION_TITLE, entryAttribute, mainAttribute));
        spec.setSpecVersion(getDefinedAttribute(Attributes.Name.SPECIFICATION_VERSION, entryAttribute, mainAttribute));
        spec.setSpecVendor(getDefinedAttribute(Attributes.Name.SPECIFICATION_VENDOR, entryAttribute, mainAttribute));
        spec.setImplTitle(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_TITLE, entryAttribute, mainAttribute));
        spec.setImplVersion(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VERSION, entryAttribute, mainAttribute));
        spec.setImplVendor(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VENDOR, entryAttribute, mainAttribute));
        if (Boolean.parseBoolean(getDefinedAttribute(Attributes.Name.SEALED, entryAttribute, mainAttribute))) {
            spec.setSealBase(new URL("jar", null, -1, jarFile.getName()));
        }
        return spec;
    }

    private static String getDefinedAttribute(Attributes.Name name, Attributes entryAttribute, Attributes mainAttribute) {
        final String value = entryAttribute == null ? null : entryAttribute.getValue(name);
        return value == null ? mainAttribute == null ? null : mainAttribute.getValue(name) : value;
    }

    public String getLibrary(final String name) {
        // JARs cannot have libraries in them
        return null;
    }

    public Resource getResource(final String name) {
        try {
            final JarFile jarFile = this.jarFile;
            String entryName = name;
            if(entryName.startsWith("/"))
                entryName = entryName.substring(1);
            final JarEntry entry = jarFile.getJarEntry(entryName);
            if (entry == null) {
                return null;
            }
            return new JarEntryResource(jarFile, entry, new URL("jar", null, -1, "file:" + jarFile.getName() + "!/" + entryName));
        } catch (MalformedURLException e) {
            // must be invalid...?  (todo: check this out)
            return null;
        }
    }

    public Collection<String> getPaths() {
        final Collection<String> index = new LinkedHashSet<String>();
        // First check for an external index
        final JarFile jarFile = this.jarFile;
        final String jarFileName = jarFile.getName();
        final File indexFile = new File(jarFileName + ".index");
        if (indexFile.exists()) {
            try {
                final BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(indexFile)));
                try {
                    String s;
                    while ((s = r.readLine()) != null) {
                        index.add(s.trim());
                    }
                    return index;
                } finally {
                    // if exception is thrown, undo index creation
                    r.close();
                }
            } catch (IOException e) {
                index.clear();
            }
        }
        // Next just read the JAR
        index.add("");
        final Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final JarEntry jarEntry = entries.nextElement();
            final String name = jarEntry.getName();
            final int idx = name.lastIndexOf('/');
            if (idx == -1) continue;
            final String path = name.substring(0, idx);
            if (path.length() == 0 || path.endsWith("/")) {
                // invalid name, just skip...
                continue;
            }
            index.add(path);
        }
        // Now try to write it
        boolean ok = false;
        try {
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(indexFile)));
            try {
                for (String name : index) {
                    writer.write(name);
                    writer.write('\n');
                }
                writer.close();
                ok = true;
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        } catch (IOException e) {
            // failed, ignore
        } finally {
            if (! ok) {
                // well, we tried...
                indexFile.delete();
            }
        }
        return index;
    }

    @Override
    public ResourceLoaderInfo createResourceLoaderInfo() {
        return new ResourceLoaderInfo(JarFileResourceLoader.class.getName(), new ArrayList<String>(getPaths()));
    }
}
