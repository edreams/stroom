/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.server;

import org.junit.Test;
import stroom.query.api.Key;
import stroom.query.api.Node;
import stroom.query.api.OffsetRange;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.VisResult;

import java.util.ArrayList;
import java.util.List;

public class TestSearchResponseMapper {
    @Test
    public void testResponse() throws Exception {
        final SearchResponseMapper mapper = new SearchResponseMapper();
        final stroom.dashboard.shared.SearchResponse result = mapper.mapResponse(getSearchResponse());
        System.out.println(result);
    }

    private SearchResponse getSearchResponse() {
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setHighlights(new String[]{"highlight1", "highlight2"});
        searchResponse.setErrors(new String[]{"some error"});
        searchResponse.setComplete(false);

        TableResult tableResult = new TableResult("table-1234");
        tableResult.setError("tableResultError");
        tableResult.setTotalResults(1);
        tableResult.setResultRange(new OffsetRange(1, 2));
        List<Row> rows = new ArrayList<>();
        String[] values = new String[1];
        values[0] = "test";
        rows.add(new Row("groupKey", values, 5));
        Row[] arr = new Row[rows.size()];
        arr = rows.toArray(arr);
        tableResult.setRows(arr);
        searchResponse.setResults(new Result[]{tableResult, getVisResult1(), getVisResult2()});

        return searchResponse;
    }

    private VisResult getVisResult1() {
        Object[][] data = new Object[8][];
        data[0] = new Object[]{"test0", 0.4, 234, "this0"};
        data[1] = new Object[]{"test1", 0.5, 25634, "this1"};
        data[2] = new Object[]{"test2", 0.6, 27, "this2"};
        data[3] = new Object[]{"test3", 0.7, 344, "this3"};
        data[4] = new Object[]{"test4", 0.2, 8984, "this4"};
        data[5] = new Object[]{"test5", 0.33, 3244, "this5"};
        data[6] = new Object[]{"test6", 34.66, 44, "this6"};
        data[7] = new Object[]{"test7", 2.33, 74, "this7"};
        VisResult visResult = new VisResult("vis-1234", new String[]{"string", "double", "integer", "string"}, null, data, null, null, null, 200L, "visResultError");

        return visResult;
    }

    private VisResult getVisResult2() {
        Object[][] data = new Object[8][];
        data[0] = new Object[]{"test0", 0.4, 234, "this0"};
        data[1] = new Object[]{"test1", 0.5, 25634, "this1"};
        data[2] = new Object[]{"test2", 0.6, 27, "this2"};
        data[3] = new Object[]{"test3", 0.7, 344, "this3"};
        data[4] = new Object[]{"test4", 0.2, 8984, "this4"};
        data[5] = new Object[]{"test5", 0.33, 3244, "this5"};
        data[6] = new Object[]{"test6", 34.66, 44, "this6"};
        data[7] = new Object[]{"test7", 2.33, 74, "this7"};

        Node[] innerNodes = new Node[2];
        innerNodes[0] = new Node(new Key("string", "innerKey1"), null, data, null, null, null);
        innerNodes[1] = new Node(new Key("string", "innerKey2"), null, data, null, null, null);

        Node[] nodes = new Node[2];
        nodes[0] = new Node(new Key("string", "key1"), innerNodes, null, null, null, null);
        nodes[1] = new Node(new Key("string", "key2"), null, data, null, null, null);

        VisResult visResult = new VisResult("vis-5555", new String[]{"string", "double", "integer", "string"}, nodes, null, null, null, null, 200L, "visResultError");

        return visResult;
    }
}
