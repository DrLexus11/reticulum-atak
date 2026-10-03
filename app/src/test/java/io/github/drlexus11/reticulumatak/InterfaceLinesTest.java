package io.github.drlexus11.reticulumatak;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Agreement with Columba, against fixtures/columba_mesh_interfaces.json. */
public class InterfaceLinesTest {
    private static JSONArray cases() throws Exception {
        try (InputStream in = InterfaceLinesTest.class.getClassLoader().getResourceAsStream("columba_mesh_interfaces.json");
                Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            return new JSONObject(scanner.useDelimiter("\\A").next()).getJSONArray("snapshots");
        }
    }

    @Test
    public void everyFixtureSaysWhatItExpects() throws Exception {
        JSONArray cases = cases();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String name = c.getString("name");
            MeshSnapshot s = MeshSnapshot.parse(c.getJSONObject("snapshot").toString());
            JSONObject expect = c.getJSONObject("expect");
            assertEquals(name, expect.getInt("interfaces"), s.interfaces.size());
            assertEquals(name, expect.getString("pending_line"), InterfaceLines.header(s));
            JSONArray rows = expect.getJSONArray("rows");
            for (int r = 0; r < rows.length(); r++)
                assertEquals(name, rows.getString(r), InterfaceLines.row(s.interfaces.get(r), s.interfacesLive));
        }
    }

    @Test
    public void applyIsOfferedOnlyWhenItHasSomethingToDo() throws Exception {
        JSONArray cases = cases();
        JSONObject staged = cases.getJSONObject(0).getJSONObject("snapshot");
        JSONObject live = cases.getJSONObject(1).getJSONObject("snapshot");
        assertTrue(InterfaceLines.canApply(MeshSnapshot.parse(staged.toString())));
        assertFalse(InterfaceLines.canApply(MeshSnapshot.parse(live.toString())));
        assertFalse(InterfaceLines.canApply(null));

        JSONObject controlOff = new JSONObject(staged.toString());
        controlOff.getJSONObject("node").put("control", false);
        assertFalse(InterfaceLines.canApply(MeshSnapshot.parse(controlOff.toString())));

        JSONObject stopped = new JSONObject(staged.toString());
        stopped.getJSONObject("node").put("running", false);
        assertFalse(InterfaceLines.canApply(MeshSnapshot.parse(stopped.toString())));

        JSONObject nothingStaged = new JSONObject(staged.toString());
        nothingStaged.put("interfaces_pending", false);
        assertFalse(InterfaceLines.canApply(MeshSnapshot.parse(nothingStaged.toString())));
    }

    @Test
    public void aSnapshotWithoutInterfacesHasNone() throws Exception {
        try (InputStream in = InterfaceLinesTest.class.getClassLoader().getResourceAsStream("columba_mesh_v1.json");
                Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            JSONObject root = new JSONObject(scanner.useDelimiter("\\A").next());
            MeshSnapshot s = MeshSnapshot.parse(root.getJSONArray("snapshots").getJSONObject(0).getJSONObject("snapshot").toString());
            assertTrue(s.interfaces.isEmpty());
        }
    }

    @Test
    public void everyResultHasALine() {
        assertEquals("Refused: it would cut this phone off", InterfaceLines.result(5));
        assertEquals("Staged: Apply to put it into effect", InterfaceLines.result(7));
        assertEquals("Not done: code 9", InterfaceLines.result(9));
        assertEquals("Not done: this Columba cannot switch interfaces",
                InterfaceLines.result(ColumbaMeshClient.NOT_SUPPORTED));
    }
}
