package org.intermine.api.bag;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.intermine.api.config.ClassKeyHelper;
import org.intermine.api.template.ApiTemplate;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.model.testmodel.Employee;
import org.intermine.model.testmodel.Manager;
import org.intermine.objectstore.*;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.Results;
import org.intermine.pathquery.PathQuery;
import org.intermine.template.TemplateQuery;
import org.intermine.template.xml.TemplateQueryBinding;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BagQueryRunnerTest {

    private ObjectStoreWriter osw;
    private ObjectStore os;
    private Map<String, Employee> eIds;
    private TestingBagQueryRunner runner, runnerMatchAll;
    Map<String, List<FieldDescriptor>> classKeys;

    @Before
    public void setUp() throws Exception {
        osw = ObjectStoreWriterFactory.getObjectStoreWriter("osw.unittest");
        os = osw.getObjectStore();
        Map data = ObjectStoreTestUtils.getTestData("testmodel", "testmodel_data.xml");
        ObjectStoreTestUtils.storeData(osw, data);

        runner = getRunner(true);
        runnerMatchAll = getRunner(false);
    }

    @After
    public void teardown() throws Exception {
        osw.close();
    }

    private TestingBagQueryRunner getRunner(boolean matchOnFirst) throws Exception {
        os = ObjectStoreFactory.getObjectStore("os.unittest");
        Properties props = new Properties();
        props.load(getClass().getClassLoader().getResourceAsStream("class_keys.properties"));
        classKeys = ClassKeyHelper.readKeys(os.getModel(), props);
        eIds = getEmployeeIds();

        InputStream is = getClass().getClassLoader().getResourceAsStream("bag-queries.xml");
        BagQueryConfig bagQueryConfig = BagQueryHelper.readBagQueryConfig(os.getModel(), is);
        bagQueryConfig.setMatchOnFirst(matchOnFirst);
        TemplateQueryBinding tqb = new TemplateQueryBinding();
        Map<String, TemplateQuery> tqs = tqb.unmarshalTemplates(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("BagQueryRunnerTest_templates.xml")), PathQuery.USERPROFILE_VERSION);

        // construct with a null TemplateManager and set conversion templates to specific list
        TestingBagQueryRunner r = new TestingBagQueryRunner(os, classKeys, bagQueryConfig, null);

        List<ApiTemplate> templates = new ArrayList<ApiTemplate>();
        for (TemplateQuery t: tqs.values()) {
            templates.add(new ApiTemplate(t));
        }
        r.setConversionTemplates(templates);
        return r;
    }

    // expect each input string to match one object
    @Test
    public void testSearchForBagMatches() throws Exception {
        List input = Arrays.asList(new Object[] {"EmployeeA1", "EmployeeA2"});
        BagQueryResult res = runner.search("Employee", input, null, true, true, false);
        Assert.assertEquals(2, res.getMatches().values().size());
        Assert.assertTrue(res.getIssues().isEmpty());
        Assert.assertTrue(res.getUnresolved().isEmpty());
    }

    // expect each input string to match one object
    @Test
    public void testCase() throws Exception {
        List input = Arrays.asList(new Object[] {"employeeA1", "employeeA2"});
        BagQueryResult res = runner.search("Employee", input, null, true, true, false);
        Assert.assertTrue(res.getMatches().isEmpty());
        Assert.assertTrue(res.getIssues().isEmpty());
        Assert.assertEquals(2, res.getUnresolved().size());
    }

    // test for the case when an identifier appears twice in the input - ignore duplicates
    @Test
    public void testSearchForBagDuplicates1() throws Exception {
        List input = Arrays.asList(new Object[] {"EmployeeA1", "EmployeeA2", "EmployeeA1"});
        BagQueryResult res = runner.searchForBag("Employee", input, null, true);
        Assert.assertEquals(2, res.getMatches().values().size());
        Assert.assertTrue(res.getIssues().isEmpty());
        Assert.assertTrue(res.getUnresolved().isEmpty());
    }

    // expect to get two objects back for 'Mr.'
    @Test
    public void testSearchForBagDuplicates2() throws Exception {
        List input = Arrays.asList(new Object[] {"Mr."});
        BagQueryResult res = runner.searchForBag("Manager", input, null, true);
        Assert.assertEquals(0, res.getMatches().size());
        Map expected = new HashMap();
        Map queries = new HashMap();
        List ids = new ArrayList(Arrays.asList(new Object[] {eIds.get("EmployeeB1"), eIds.get("EmployeeB3")}));
        Map results = new HashMap();
        results.put("Mr.", ids);
        queries.put(BagQueryHelper.DEFAULT_MESSAGE, results);
        expected.put(BagQueryResult.DUPLICATE, queries);
        Assert.assertEquals(expected, res.getIssues());
        Assert.assertTrue(res.getUnresolved().isEmpty());
    }

    // expect one match and one unresolved
    @Test
    public void testSearchForBagUnresolved() throws Exception {
        List input = Arrays.asList(new Object[] {"EmployeeA1", "rubbish"});
        BagQueryResult res = runner.searchForBag("Employee", input, null, true);
        Assert.assertEquals(1, res.getMatches().values().size());
        Assert.assertTrue(res.getIssues().isEmpty());
        Assert.assertEquals(res.getUnresolved().size(), 1);
    }

    // two identifiers for same object - both match once
    // in this case there are two entries in matches but both have
    // the same id - so getMatches().values().size() == 1
    @Test
    public void testSearchForBagDoubleInput1() throws Exception {
        List input = Arrays.asList(new Object[] {"EmployeeB1", "Mr."});
        BagQueryResult res = runner.searchForBag("CEO", input, null, true);
        Assert.assertEquals(1, res.getMatches().size());
        Assert.assertEquals(2, ((List) ((Collection) res.getMatches().values()).iterator().next()).size());
        Assert.assertTrue(res.getIssues().isEmpty());
        Assert.assertTrue(res.getUnresolved().isEmpty());
    }

    // two identifiers for same object - one matches twice
    @Test
    public void testSearchForBagDoubleInput2() throws Exception {
        List input = Arrays.asList(new Object[] {"EmployeeB1", "Mr."});
        BagQueryResult res = runner.searchForBag("Manager", input, null, true);
        Assert.assertEquals(1, res.getMatches().size());
        Map expected = new HashMap();
        Map queries = new HashMap();
        List ids = new ArrayList(Arrays.asList(new Object[] {eIds.get("EmployeeB1"), eIds.get("EmployeeB3")}));
        Map results = new HashMap();
        results.put("Mr.", ids);
        queries.put(BagQueryHelper.DEFAULT_MESSAGE, results);
        expected.put(BagQueryResult.DUPLICATE, queries);
        Assert.assertEquals(expected, res.getIssues());
        Assert.assertTrue(res.getUnresolved().isEmpty());
    }

    // match nothing from first query, match both from second
    @Test
    public void testSecondQueryIssue() throws Exception {
        List input = Arrays.asList(new Object[] {"1"});
        BagQueryResult res = runner.searchForBag("Employee", input, null, true);
        Assert.assertEquals(0, res.getMatches().values().size());
        Map expected = new HashMap();
        Map queries = new HashMap();
        List ids = new ArrayList(Arrays.asList(new Object[] {eIds.get("EmployeeA1")}));
        Map results = new HashMap();
        results.put("1", ids);
        queries.put("employee end", results);
        expected.put(BagQueryResult.OTHER, queries);
        Assert.assertEquals(expected, res.getIssues());
        Assert.assertTrue(res.getUnresolved().isEmpty());
    }

    // test searching for an input string that doesn't match any object
    @Test
    public void testObjectNotFound() throws Exception {
        String nonMatchingString = "non_matching_string";
        List input = Arrays.asList(new Object[] {nonMatchingString});
        BagQueryResult res = runner.searchForBag("Employee", input, null, true);
        Assert.assertEquals(0, res.getMatches().values().size());
        Map<String, Object> resUnresolved = res.getUnresolved();
        Assert.assertTrue(resUnresolved.size() == 1);
        Assert.assertTrue(resUnresolved.containsKey(nonMatchingString));
        Assert.assertNull(resUnresolved.get(nonMatchingString));
    }

    // test searching for an input string that only matches an object of the wrong type and can't
    // be converted
    @Test
    public void testObjectWrongType() throws Exception {
        String ceoName = "Tatjana Berkel";
        List<String> input = Arrays.asList(ceoName);
        BagQueryResult res = runner.searchForBag("Contractor", input, null, true);
        Assert.assertEquals(0, res.getMatches().values().size());
        Collection<String> resUnresolved = res.getUnresolvedIdentifiers();
        Assert.assertEquals(1, resUnresolved.size());
        Assert.assertTrue(resUnresolved.contains(ceoName));
        Assert.assertEquals(ceoName, resUnresolved.iterator().next());
    }

    // test searching for an input string that has to be converted
    @Test
    public void testTypeConverted() throws Exception {
        String empName = "EmployeeA2";
        List input = Arrays.asList(new Object[] {empName});
        BagQueryResult res = runner.searchForBag("Manager", input, null, true);
        Assert.assertEquals(0, res.getMatches().values().size());
        Map issues = res.getIssues();

        Map translated = (Map) issues.get(BagQueryResult.TYPE_CONVERTED);
        Assert.assertEquals(1, translated.values().size());
        Map resUnresolved = res.getUnresolved();
        Assert.assertTrue(resUnresolved.size() == 0);
    }

    // test searching for an input string that has to be converted
    @Test
    public void testTypeConvertedAndNotConverted() throws Exception {
        String empName1 = "EmployeeA1";
        String empName2 = "EmployeeA2";
        List input = Arrays.asList(new Object[] {empName1, empName2});
        BagQueryResult res = runner.searchForBag("Manager", input, null, true);
        Assert.assertEquals(1, res.getMatches().values().size());
        Assert.assertEquals(empName1, ((List) res.getMatches().values().iterator().next()).get(0));
        Map issues = res.getIssues();

        Map converted = (Map) issues.get(BagQueryResult.TYPE_CONVERTED);
        Assert.assertEquals(1, converted.values().size());
        Map convertedInputToObjs = (Map) converted.values().iterator().next();
        Assert.assertEquals(1, convertedInputToObjs.size());
        Assert.assertEquals(empName2, convertedInputToObjs.keySet().iterator().next());
        List convertedPairs = (List) convertedInputToObjs.values().iterator().next();
        Assert.assertEquals(1, convertedPairs.size());
        ConvertedObjectPair pair = (ConvertedObjectPair) convertedPairs.get(0);
        Assert.assertEquals("org.intermine.model.testmodel.Manager", pair.getNewObject().getClass().getName());
        Assert.assertEquals("EmployeeA2", ((Employee) pair.getOldObject()).getName());
        Assert.assertEquals("EmployeeA1", ((Manager) pair.getNewObject()).getName());
        Map resUnresolved = res.getUnresolved();
        Assert.assertTrue(resUnresolved.size() == 0);
    }

    // test a type conversion that has many results for an input identifier
    @Test
    public void testTypeConvertedMultipleResults() throws Exception {
        String managerName = "ContractorA";
        List input = Arrays.asList(new Object[] {managerName});
        BagQueryResult res = runner.searchForBag("Employee", input, null, true);
        Assert.assertEquals(0, res.getMatches().values().size());
        Map issues = res.getIssues();
        Map converted = (Map) issues.get(BagQueryResult.TYPE_CONVERTED);
        Assert.assertEquals(1, converted.size());
        Map convertedObjectMap =
            (Map) converted.get("Employable by name found by converting from x");
        Assert.assertEquals(1, convertedObjectMap.size());
        List emps = (List) convertedObjectMap.get(managerName);
        Assert.assertEquals(6, emps.size());
    }

    // test that getMatchandIssueIds returns all ids - expect one match, one
    // duplicate (two ids) and one unresolved
    @Test
    public void testGetMatchAndIssueIds() throws Exception {
        List<String> input = Arrays.asList("EmployeeA1", "Mr.", "gibbon");
        BagQueryResult res = runner.searchForBag("Manager", input, null, true);
        Assert.assertEquals(1, res.getMatches().size());
        Set<Integer> ids = new HashSet<Integer>(Arrays.asList(new Integer[] {
            eIds.get("EmployeeB1").getId(),
            eIds.get("EmployeeA1").getId(),
            eIds.get("EmployeeB3").getId()}));
        Assert.assertEquals(ids, res.getMatchAndIssueIds());
        Assert.assertEquals(1, res.getUnresolvedIdentifiers().size());
        Assert.assertEquals(Collections.singleton("gibbon"), res.getUnresolvedIdentifiers());
    }

    @Test
    public void testWildcards() throws Exception {
        List<String> input = Arrays.asList("EmployeeA*", "EmployeeB3");
        BagQueryResult res = runner.searchForBag("Employee", input, null, true);
        Set<Integer> ids = new HashSet<Integer>(Arrays.asList(eIds.get("EmployeeA3").getId(), eIds.get("EmployeeA2").getId(), eIds.get("EmployeeA1").getId()));
        Assert.assertEquals(ids, new HashSet(res.getIssues().get(BagQueryResult.WILDCARD).get("searching key fields").get("EmployeeA*")));
    }

    // we need to test a query that matches a different type.  Probably
    // need to add another query to: testmodel/webapp/main/resources/webapp/WEB-INF/bag-queries.xml
    private Map<String, Employee> getEmployeeIds() throws ObjectStoreException {
        Map<String, Employee> employees = new HashMap<String, Employee>();
        Query q = new Query();
        QueryClass qc = new QueryClass(Employee.class);
        q.addFrom(qc);
        q.addToSelect(qc);
        Results res = os.execute(q);
        Iterator resIter = res.iterator();
        while (resIter.hasNext()) {
            Employee e = (Employee) ((List) resIter.next()).get(0);
            employees.put(e.getName(), e);
        }
        return employees;
    }

    // match all
    @Test
    public void testSearchForBagMatchesMatchAll() throws Exception {
        List input = Arrays.asList(new Object[] {"EmployeeA1", "EmployeeA2"});
        BagQueryResult res = runnerMatchAll.searchForBag("Employee", input, null, true);
        Assert.assertEquals(2, res.getMatches().values().size());
        Assert.assertTrue("Should have issues", !res.getIssues().isEmpty());
        Assert.assertTrue("Should have no unresolved identifiers", res.getUnresolved().isEmpty());
    }
}
