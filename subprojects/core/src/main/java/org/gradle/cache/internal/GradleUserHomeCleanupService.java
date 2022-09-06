/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.cache.internal;

import org.gradle.cache.scopes.GlobalScopedCache;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.io.File;

public class GradleUserHomeCleanupService implements Stoppable {

    private static final long MAX_UNUSED_DAYS_FOR_RELEASES = 30;
    private static final long MAX_UNUSED_DAYS_FOR_SNAPSHOTS = 7;

    private final Deleter deleter;
    private final GradleUserHomeDirProvider userHomeDirProvider;
    private final GlobalScopedCache globalScopedCache;
    private final UsedGradleVersions usedGradleVersions;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final CacheCleanupEnablement cacheCleanupEnablement;

    public GradleUserHomeCleanupService(
        Deleter deleter,
        GradleUserHomeDirProvider userHomeDirProvider,
        GlobalScopedCache globalScopedCache,
        UsedGradleVersions usedGradleVersions,
        ProgressLoggerFactory progressLoggerFactory,
        CacheCleanupEnablement cacheCleanupEnablement
    ) {
        this.deleter = deleter;
        this.userHomeDirProvider = userHomeDirProvider;
        this.globalScopedCache = globalScopedCache;
        this.usedGradleVersions = usedGradleVersions;
        this.progressLoggerFactory = progressLoggerFactory;
        this.cacheCleanupEnablement = cacheCleanupEnablement;
    }

    @Override
    public void stop() {
        if (cacheCleanupEnablement.isEnabled()) {
            File cacheBaseDir = globalScopedCache.getRootDir();
            boolean wasCleanedUp = execute(
                new VersionSpecificCacheCleanupAction(cacheBaseDir, MAX_UNUSED_DAYS_FOR_RELEASES, MAX_UNUSED_DAYS_FOR_SNAPSHOTS, deleter));
            if (wasCleanedUp) {
                execute(new WrapperDistributionCleanupAction(userHomeDirProvider.getGradleUserHomeDirectory(), usedGradleVersions));
            }
        }
    }

    private boolean execute(DirectoryCleanupAction action) {
        ProgressLogger progressLogger = startNewOperation(action.getClass(), action.getDisplayName());
        try {
            return action.execute(new DefaultCleanupProgressMonitor(progressLogger));
        } finally {
            progressLogger.completed();
        }
    }

    private ProgressLogger startNewOperation(Class<?> loggerClass, String description) {
        return progressLoggerFactory.newOperation(loggerClass).start(description, description);
    }
}
