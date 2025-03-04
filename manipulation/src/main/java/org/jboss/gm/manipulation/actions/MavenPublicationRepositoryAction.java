package org.jboss.gm.manipulation.actions;

import static org.apache.commons.lang.StringUtils.isEmpty;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.tasks.Upload;
import org.gradle.authentication.http.HttpHeaderAuthentication;
import org.jboss.gm.common.Configuration;

/**
 * Adds a publication repository to the legacy maven plugin.
 *
 * Repository URL and authentication token need to be configured externally.
 *
 * Performed configuration is equivalent to following gradle snippet:
 *
 * <pre>
 *     uploadArchives {
 *         repositories {
 *             maven {
 *                 url = System.getProperty('AProxDeployUrl')
 *                 credentials(HttpHeaderCredentials) {
 *                     name = "Authorization"
 *                     value = "Bearer " + System.getProperty('accessToken')
 *                 }
 *                 authentication {
 *                     header(HttpHeaderAuthentication)
 *                 }
 *             }
 *         }
 *     }
 * </pre>
 */
public class MavenPublicationRepositoryAction implements Action<Project> {

    @Override
    public void execute(Project project) {
        if (!project.getPluginManager().hasPlugin("maven")) {
            project.getLogger().warn("Legacy 'maven' plugin not detected, skipping publishing repository creation.");
        }

        Upload uploadArchives = project.getTasks().withType(Upload.class).findByName("uploadArchives");
        if (uploadArchives == null) {
            project.getLogger().info("Creating uploadArchives task");
            uploadArchives = project.getTasks().create("uploadArchives", Upload.class);
        } else {
            uploadArchives.getRepositories().clear();
        }

        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        if (isEmpty(config.deployUrl())) {
            project.getLogger().warn("Publishing URL was not configured.");
            return;
        }

        if (isEmpty(config.accessToken())) {
            project.getLogger().warn("No authentication token was configured.");
        }

        // add a maven repository and configure authentication token
        uploadArchives.getRepositories().maven(mavenArtifactRepository -> {
            mavenArtifactRepository.setName("Manipulator Publishing Repository");
            mavenArtifactRepository.setUrl(config.deployUrl());
            if (config.accessToken() != null) {
                mavenArtifactRepository.credentials(HttpHeaderCredentials.class, cred -> {
                    cred.setName("Authorization");
                    cred.setValue("Bearer " + config.accessToken());
                });
                mavenArtifactRepository.getAuthentication().create("header", HttpHeaderAuthentication.class);
            }
        });

        // TODO: investigate better way of doing this
        // We assume that "install" task generates project's POM. We want this POM to be published by "uploadArchives"
        // task. To do that, reference to this file must be added to "uploadArchives" configuration artifacts, but
        // not to "install" configuration artifacts. By default, the two tasks share the same configuration ("archives").
        // We therefore create two distinct configurations, copy artifacts from the original configuration to the new
        // ones, and assign them to the respective tasks. Reference to generated POM will then be added to configuration
        // used by "uploadArchives" task.

        // ensure that the "install" task is automatically invoked before the "uploadArchives"
        uploadArchives.dependsOn("install");

        // create two new configurations and copy over the original artifacts
        org.gradle.api.artifacts.Configuration archives = project.getConfigurations().getByName("archives");
        org.gradle.api.artifacts.Configuration installArchives = project.getConfigurations().create("installArchives");
        installArchives.getArtifacts().addAll(archives.getArtifacts());
        org.gradle.api.artifacts.Configuration publishArchives = project.getConfigurations().create("publishArchives");
        publishArchives.getArtifacts().addAll(archives.getArtifacts());

        // add an artifact referencing the POM
        project.getArtifacts().add("publishArchives",
                project.file(project.getBuildDir().toPath().resolve("poms/pom-default.xml")),
                configurablePublishArtifact -> {
                    configurablePublishArtifact.setName(project.getName());
                    configurablePublishArtifact.setExtension("pom");
                });

        // configure "install" and "uploadArchives" to use the new configurations
        Upload install = project.getTasks().withType(Upload.class).getByName("install");
        install.setConfiguration(installArchives);
        uploadArchives.setConfiguration(publishArchives);
    }
}
