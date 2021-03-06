package org.schemaspy.testcontainer;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.schemaspy.Config;
import org.schemaspy.cli.CommandLineArguments;
import org.schemaspy.model.Database;
import org.schemaspy.model.ProgressListener;
import org.schemaspy.model.Table;
import org.schemaspy.model.TableColumn;
import org.schemaspy.service.DatabaseService;
import org.schemaspy.service.SqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.jdbc.ext.ScriptUtils;
import org.testcontainers.utility.JdbcDriverUtil;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest
public class OracleIT {

    @Autowired
    private SqlService sqlService;

    @Autowired
    private DatabaseService databaseService;

    @Mock
    private ProgressListener progressListener;

    @MockBean
    private CommandLineArguments arguments;

    @MockBean
    private CommandLineRunner commandLineRunner;

    private static final String driverPath = "ext-lib/ojdbc*";

    private static Database database;

    @ClassRule
    public static OracleContainer oracleContainer = new OracleContainer<>()
            .assumeDocker()
            .withDrivers(driverPath);

    @Before
    public synchronized void gatheringSchemaDetailsTest() throws SQLException, IOException, ScriptException, URISyntaxException {
        if (database == null) {
            setupOracle();
            createDatabaseRepresentation();
        }
    }

    private void setupOracle() throws SQLException, IOException, ScriptException {
        Connection conn = oracleContainer.createConnection("");
        String scriptPath = "/integrationTesting/dbScripts/oracle.sql";
        String script = IOUtils.toString(this.getClass().getResourceAsStream( scriptPath), StandardCharsets.UTF_8);
        ScriptUtils.executeSqlScript(conn,scriptPath, script);
    }

    private void createDatabaseRepresentation() throws SQLException, IOException, URISyntaxException {
        Path driverPathPath = Paths.get(JdbcDriverUtil.getDriversFromPath(driverPath).get(0).toURI());
        String[] args = {
                "-t", "orathin",
                "-dp", driverPathPath.toString(),
                "-db", oracleContainer.getSid(),
                "-s", "ORAIT",
                "-cat", "%",
                "-o", "target/integrationtesting/orait",
                "-u", "orait",
                "-p", "orait123",
                "-host", oracleContainer.getContainerIpAddress(),
                "-port", oracleContainer.getOraclePort().toString()
        };
        given(arguments.getOutputDirectory()).willReturn(new File("target/integrationtesting/databaseServiceIT"));
        given(arguments.getDatabaseType()).willReturn("orathin");
        given(arguments.getUser()).willReturn("orait");
        given(arguments.getSchema()).willReturn("ORAIT");
        given(arguments.getCatalog()).willReturn("%");
        given(arguments.getDatabaseName()).willReturn(oracleContainer.getSid());
        Config config = new Config(args);
        DatabaseMetaData databaseMetaData = sqlService.connect(config);
        Database database = new Database(config, databaseMetaData, arguments.getDatabaseName(), arguments.getCatalog(), arguments.getSchema(), null, progressListener);
        databaseService.gatheringSchemaDetails(config, database, progressListener);
        this.database = database;
    }

    @Test
    public void databaseShouldBePopulatedWithTableTest() {
        Table table = getTable("TEST");
        assertThat(table).isNotNull();
    }

    @Test
    public void databaseShouldBePopulatedWithTableTestAndHaveColumnName() {
        Table table = getTable("TEST");
        TableColumn column = table.getColumn("NAME");
        assertThat(column).isNotNull();
    }

    @Test
    public void databaseShouldBePopulatedWithTableTestAndHaveColumnNameWithComment() {
        Table table = getTable("TEST");
        TableColumn column = table.getColumn("NAME");
        assertThat(column.getComments()).isEqualToIgnoringCase("the name");
    }

    private Table getTable(String tableName) {
        return database.getTables().stream().filter(table -> table.getName().equals(tableName)).findFirst().get();
    }
}
