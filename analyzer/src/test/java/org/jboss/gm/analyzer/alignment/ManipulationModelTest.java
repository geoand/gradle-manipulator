package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Test;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class ManipulationModelTest {

    @Test
    public void findCorrespondingChildWithName() {
        ManipulationModel model = new ManipulationModel("root", "bar");
        final ManipulationModel child = new ManipulationModel("child1", "bar");
        model.addChild(child);
        model.addChild(new ManipulationModel("child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "bar");
        child.addChild(child11);

        assertThat(model.findCorrespondingChild(model.getName())).isEqualTo(model);
        assertThat(model.findCorrespondingChild("child1")).isEqualTo(child);

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> model.findCorrespondingChild("child11"))
                .withMessage("ManipulationModel child11 does not exist");

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> model.findCorrespondingChild(""))
                .withMessage("Supplied child name cannot be empty");

        assertThat(child.findCorrespondingChild("child11")).isEqualTo(child11);
    }

    @Test
    public void findCorrespondingChildWithPath() {
        ManipulationModel model = new ManipulationModel("root", "bar");
        final ManipulationModel child = new ManipulationModel("child1", "bar");
        model.addChild(child);
        model.addChild(new ManipulationModel("child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "bar");
        child.addChild(child11);
        final ManipulationModel child111 = new ManipulationModel("child111", "bar");
        child11.addChild(child111);

        assertThat(model.findCorrespondingChild(":")).isEqualTo(model);

        assertThat(model.findCorrespondingChild(":child1")).isEqualTo(child);

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> model.findCorrespondingChild(":child11"))
                .withMessage("ManipulationModel child11 does not exist");

        assertThat(child.findCorrespondingChild(":child1:child11")).isEqualTo(child11);
        assertThat(child.findCorrespondingChild(":child1:child11:child111")).isEqualTo(child111);
    }

    @Test
    public void getAllAlignedDependencies() {
        final ManipulationModel root = new ManipulationModel("root", "bar");
        root.getAlignedDependencies().put("org.jboss.resteasy:resteasy-jaxrs:3.6.3.Final",
                new SimpleProjectVersionRef("org.jboss.resteasy", "resteasy-jaxrs", "3.6.3.Final-redhat-000001"));
        final ManipulationModel child = new ManipulationModel("child1", "bar");
        child.getAlignedDependencies().put("org.hibernate:hibernate-core:5.3.7.Final",
                new SimpleProjectVersionRef("org.hibernate", "hibernate-core", "5.3.7.Final-redhat-000001"));
        root.addChild(child);
        root.addChild(new ManipulationModel("child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "bar");
        child.addChild(child11);
        child11.getAlignedDependencies().put("io.undertow:undertow-core:2.0.15.Final",
                new SimpleProjectVersionRef("io.undertow", "undertow-core", "2.0.15.Final-redhat-000001"));
        child11.getAlignedDependencies().put("org.mockito:mockito-core:2.27.0",
                new SimpleProjectVersionRef("io.undertow", "undertow-core", "2.27.0-redhat-000001"));

        assertThat(root.getAllAlignedDependencies()).containsOnlyKeys(
                "org.jboss.resteasy:resteasy-jaxrs:3.6.3.Final", "org.hibernate:hibernate-core:5.3.7.Final",
                "io.undertow:undertow-core:2.0.15.Final", "org.mockito:mockito-core:2.27.0")
                .satisfies(m -> assertThat(m.values()).containsOnly(
                        new SimpleProjectVersionRef("org.hibernate", "hibernate-core", "5.3.7.Final-redhat-000001"),
                        new SimpleProjectVersionRef("org.jboss.resteasy", "resteasy-jaxrs", "3.6.3.Final-redhat-000001"),
                        new SimpleProjectVersionRef("io.undertow", "undertow-core", "2.0.15.Final-redhat-000001"),
                        new SimpleProjectVersionRef("io.undertow", "undertow-core", "2.27.0-redhat-000001")));
    }
}
