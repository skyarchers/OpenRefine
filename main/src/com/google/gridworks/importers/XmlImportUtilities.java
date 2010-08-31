package com.google.gridworks.importers;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gridworks.model.Cell;
import com.google.gridworks.model.Column;
import com.google.gridworks.model.Project;
import com.google.gridworks.model.Row;

public class XmlImportUtilities {
    final static Logger logger = LoggerFactory.getLogger("XmlImporterUtilities");

    /**
     * An element which holds sub-elements we
     * shall import as records
     */
    static protected class RecordElementCandidate {
        String[] path;
        int count;
    }

    /**
     *
     *
     *
     */
    static protected abstract class ImportVertical {
        public String name = "";
        public int nonBlankCount;

        abstract void tabulate();
    }

    /**
     * A column group describes a branch in tree structured data
     */
    static public class ImportColumnGroup extends ImportVertical {
        public Map<String, ImportColumnGroup> subgroups = new HashMap<String, ImportColumnGroup>();
        public Map<String, ImportColumn> columns = new HashMap<String, ImportColumn>();
        public int nextRowIndex;

        @Override
        void tabulate() {
            for (ImportColumn c : columns.values()) {
                c.tabulate();
                nonBlankCount = Math.max(nonBlankCount, c.nonBlankCount);
            }
            for (ImportColumnGroup g : subgroups.values()) {
                g.tabulate();
                nonBlankCount = Math.max(nonBlankCount, g.nonBlankCount);
            }
        }
    }

    /**
     * A column is used to describe a branch-terminating element in a tree structure
     *
     */
    static public class ImportColumn extends ImportVertical {
        public int      cellIndex;
        public int      nextRowIndex;
        public boolean  blankOnFirstRow;

        public ImportColumn() {}
        
        public ImportColumn(String name) { //required for testing
            super.name = name;
        }

        @Override
        void tabulate() {
            // already done the tabulation elsewhere
        }
    }

    /**
     * A record describes a data element in a tree-structure
     *
     */
    static public class ImportRecord {
        public List<List<Cell>> rows = new LinkedList<List<Cell>>();
    }

    static public String[] detectPathFromTag(InputStream inputStream, String tag) {
        try {
            XMLStreamReader parser = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);

            while (parser.hasNext()) {
                int eventType = parser.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    List<String> path = detectRecordElement(parser, tag);
                    if (path != null) {
                        String[] path2 = new String[path.size()];

                        path.toArray(path2);

                        return path2;
                    }
                }
            }
        } catch (Exception e) {
            // silent
            // e.printStackTrace();
        }

        return null;
    }

    /**
     * Looks for an element with the given tag name in the Xml being parsed, returning the path hierarchy to reach it.
     *
     * @param parser
     * @param tag
     *         The Xml element name (can be qualified) to search for
     * @return
     *         If the tag is found, an array of strings is returned.
     *         If the tag is at the top level, the tag will be the only item in the array.
     *         If the tag is nested beneath the top level, the array is filled with the hierarchy with the tag name at the last index
     *         Null if the the tag is not found.
     * @throws XMLStreamException
     */
    static protected List<String> detectRecordElement(XMLStreamReader parser, String tag) throws XMLStreamException {
        if(parser.getEventType() == XMLStreamConstants.START_DOCUMENT)
            parser.next();
        String localName = parser.getLocalName();
        String fullName = composeName(parser.getPrefix(), localName);
        if (tag.equals(parser.getLocalName()) || tag.equals(fullName)) {
            List<String> path = new LinkedList<String>();
            path.add(localName);

            return path;
        }

        while (parser.hasNext()) {
            int eventType = parser.next();
            if (eventType == XMLStreamConstants.END_ELEMENT) {
                break;
            } else if (eventType == XMLStreamConstants.START_ELEMENT) {
                List<String> path = detectRecordElement(parser, tag);
                if (path != null) {
                    path.add(0, localName);
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * Seeks for recurring XML element in an InputStream
     * which are likely candidates for being data records
     * @param inputStream
     *              The XML data as a stream
     * @return
     *              The path to the most numerous of the possible candidates.
     *              null if no candidates were found (less than 6 recurrences)
     */
    static public String[] detectRecordElement(InputStream inputStream) {
        logger.trace("detectRecordElement(inputStream)");
        List<RecordElementCandidate> candidates = new ArrayList<RecordElementCandidate>();

        try {
            XMLStreamReader parser = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);

            while (parser.hasNext()) {
                int eventType = parser.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    RecordElementCandidate candidate =
                        detectRecordElement(
                            parser,
                            new String[] { parser.getLocalName() });

                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        } catch (Exception e) {
            // silent
            // e.printStackTrace();
        }

        if (candidates.size() > 0) {
            sortRecordElementCandidates(candidates);

            return candidates.get(0).path;
        }
        logger.info("No candidate elements were found in Xml - at least 6 similar elements are required");
        return null;
    }

    static protected RecordElementCandidate detectRecordElement(XMLStreamReader parser, String[] path) {
        logger.trace("detectRecordElement(XMLStreamReader, String[])");
        List<RecordElementCandidate> descendantCandidates = new ArrayList<RecordElementCandidate>();

        Map<String, Integer> immediateChildCandidateMap = new HashMap<String, Integer>();
        int textNodeCount = 0;
        int childElementNodeCount = 0;

        try {
            while (parser.hasNext()) {
                int eventType = parser.next();
                if (eventType == XMLStreamConstants.END_ELEMENT) {
                    break;
                } else if (eventType == XMLStreamConstants.CHARACTERS) {
                    if (parser.getText().trim().length() > 0) {
                        textNodeCount++;
                    }
                } else if (eventType == XMLStreamConstants.START_ELEMENT) {
                    childElementNodeCount++;

                    String tagName = parser.getLocalName();

                    immediateChildCandidateMap.put(
                        tagName,
                        immediateChildCandidateMap.containsKey(tagName) ?
                                immediateChildCandidateMap.get(tagName) + 1 : 1);

                    String[] path2 = new String[path.length + 1];
                    System.arraycopy(path, 0, path2, 0, path.length);
                    path2[path.length] = tagName;

                    RecordElementCandidate c = detectRecordElement(parser, path2);
                    if (c != null) {
                        descendantCandidates.add(c);
                    }
                }
            }
        } catch (Exception e) {
            // silent
            // e.printStackTrace();
        }

        if (textNodeCount > 0 && childElementNodeCount > 0) {
            // This is a mixed element
            return null;
        }

        if (immediateChildCandidateMap.size() > 0) {
            List<RecordElementCandidate> immediateChildCandidates = new ArrayList<RecordElementCandidate>(immediateChildCandidateMap.size());
            for (Entry<String, Integer> entry : immediateChildCandidateMap.entrySet()) {
                int count = entry.getValue();
                if (count > 1) {
                    String[] path2 = new String[path.length + 1];
                    System.arraycopy(path, 0, path2, 0, path.length);
                    path2[path.length] = entry.getKey();

                    RecordElementCandidate candidate = new RecordElementCandidate();
                    candidate.path = path2;
                    candidate.count = count;
                    immediateChildCandidates.add(candidate);
                }
            }

            if (immediateChildCandidates.size() > 0 && immediateChildCandidates.size() < 5) {
                // There are some promising immediate child elements, but not many,
                // that can serve as record elements.

                sortRecordElementCandidates(immediateChildCandidates);

                RecordElementCandidate ourCandidate = immediateChildCandidates.get(0);
                logger.trace("ourCandidate.count : " + ourCandidate.count + "; immediateChildCandidates.size() : " + immediateChildCandidates.size());
                if (ourCandidate.count / immediateChildCandidates.size() > 5) {
                    return ourCandidate;
                }

                descendantCandidates.add(ourCandidate);
            }
        }

        if (descendantCandidates.size() > 0) {
            sortRecordElementCandidates(descendantCandidates);

            RecordElementCandidate candidate = descendantCandidates.get(0);
            if (candidate.count / descendantCandidates.size() > 5) {
                return candidate;
            }
        }

        return null;
    }

    static public void sortRecordElementCandidates(List<RecordElementCandidate> list) {
        Collections.sort(list, new Comparator<RecordElementCandidate>() {
            public int compare(RecordElementCandidate o1, RecordElementCandidate o2) {
                return o2.count - o1.count;
            }
        });
    }

    static public void importXml(
        InputStream inputStream,
        Project project,
        String[] recordPath,
        ImportColumnGroup rootColumnGroup
    ) {
        try {
            XMLStreamReader parser = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);

            while (parser.hasNext()) {
                int eventType = parser.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    findRecord(project, parser, recordPath, 0, rootColumnGroup);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // silent
        }
    }

    static public void createColumnsFromImport(
        Project project,
        ImportColumnGroup columnGroup
    ) {
        int startColumnIndex = project.columnModel.columns.size();

        List<ImportColumn> columns = new ArrayList<ImportColumn>(columnGroup.columns.values());
        Collections.sort(columns, new Comparator<ImportColumn>() {
            public int compare(ImportColumn o1, ImportColumn o2) {
                if (o1.blankOnFirstRow != o2.blankOnFirstRow) {
                    return o1.blankOnFirstRow ? 1 : -1;
                }

                int c = o2.nonBlankCount - o1.nonBlankCount;
                return c != 0 ? c : (o1.name.length() - o2.name.length());
            }
        });

        for (int i = 0; i < columns.size(); i++) {
            ImportColumn c = columns.get(i);

            Column column = new com.google.gridworks.model.Column(c.cellIndex, c.name);
            project.columnModel.columns.add(column);
        }

        List<ImportColumnGroup> subgroups = new ArrayList<ImportColumnGroup>(columnGroup.subgroups.values());
        Collections.sort(subgroups, new Comparator<ImportColumnGroup>() {
            public int compare(ImportColumnGroup o1, ImportColumnGroup o2) {
                int c = o2.nonBlankCount - o1.nonBlankCount;
                return c != 0 ? c : (o1.name.length() - o2.name.length());
            }
        });

        for (ImportColumnGroup g : subgroups) {
            createColumnsFromImport(project, g);
        }

        int endColumnIndex = project.columnModel.columns.size();
        int span = endColumnIndex - startColumnIndex;
        if (span > 1 && span < project.columnModel.columns.size()) {
            project.columnModel.addColumnGroup(startColumnIndex, span, startColumnIndex);
        }
    }

    /**
     *
     * @param project
     * @param parser
     * @param recordPath
     * @param pathIndex
     * @param rootColumnGroup
     * @throws XMLStreamException
     */
    static protected void findRecord(
        Project project,
        XMLStreamReader parser,
        String[] recordPath,
        int pathIndex,
        ImportColumnGroup rootColumnGroup
    ) throws XMLStreamException {
        if(parser.getEventType() == XMLStreamConstants.START_DOCUMENT){
            logger.warn("Cannot use findRecord method for START_DOCUMENT event");
            return;
        }
        String tagName = parser.getLocalName();
        if (tagName.equals(recordPath[pathIndex])) {
            if (pathIndex < recordPath.length - 1) {
                while (parser.hasNext()) {
                    int eventType = parser.next();
                    if (eventType == XMLStreamConstants.START_ELEMENT) {
                        findRecord(project, parser, recordPath, pathIndex + 1, rootColumnGroup);
                    } else if (eventType == XMLStreamConstants.END_ELEMENT) {
                        break;
                    }
                }
            } else {
                processRecord(project, parser, rootColumnGroup);
            }
        } else {
            skip(parser);
        }
    }

    static protected void skip(XMLStreamReader parser) throws XMLStreamException {
        while (parser.hasNext()) {
            int eventType = parser.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                skip(parser);
            } else if (eventType == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    /**
     * processRecord parsesXml for a single element and it's sub-elements,
     * adding the parsed data as a row to the project
     * @param project
     * @param parser
     * @param rootColumnGroup
     * @throws XMLStreamException
     */
    static protected void processRecord(
        Project project,
        XMLStreamReader parser,
        ImportColumnGroup rootColumnGroup
    ) throws XMLStreamException {
        ImportRecord record = new ImportRecord();

        processSubRecord(project, parser, rootColumnGroup, record);

        if (record.rows.size() > 0) {
            for (List<Cell> row : record.rows) {
                Row realRow = new Row(row.size());
                int cellCount = 0;

                for (int c = 0; c < row.size(); c++) {
                    Cell cell = row.get(c);
                    if (cell != null) {
                        realRow.setCell(c, cell);
                        cellCount++;
                    }
                }
                
                if (cellCount > 0) {
                    project.rows.add(realRow);
                }
            }
        }
    }

    static protected String composeName(String prefix, String localName) {
        return prefix != null && prefix.length() > 0 ? (prefix + ":" + localName) : localName;
    }

    /**
     *
     * @param project
     * @param parser
     * @param columnGroup
     * @param record
     * @throws XMLStreamException
     */
    static protected void processSubRecord(
        Project project,
        XMLStreamReader parser,
        ImportColumnGroup columnGroup,
        ImportRecord record
    ) throws XMLStreamException {
        ImportColumnGroup thisColumnGroup = getColumnGroup(
                project,
                columnGroup,
                composeName(parser.getPrefix(), parser.getLocalName()));
        
        thisColumnGroup.nextRowIndex = Math.max(thisColumnGroup.nextRowIndex, columnGroup.nextRowIndex);
        
        int attributeCount = parser.getAttributeCount();
        for (int i = 0; i < attributeCount; i++) {
            String text = parser.getAttributeValue(i).trim();
            if (text.length() > 0) {
                addCell(
                    project,
                    thisColumnGroup,
                    record,
                    composeName(parser.getAttributePrefix(i), parser.getAttributeLocalName(i)),
                    text
                );
            }
        }

        while (parser.hasNext()) {
            int eventType = parser.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                processSubRecord(
                    project,
                    parser,
                    thisColumnGroup,
                    record
                );
            } else if (//eventType == XMLStreamConstants.CDATA ||
                        eventType == XMLStreamConstants.CHARACTERS) {
                String text = parser.getText().trim();
                if (text.length() > 0) {
                    addCell(
                        project,
                        thisColumnGroup,
                        record,
                        null,
                        parser.getText()
                    );
                }
            } else if (eventType == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        
        int nextRowIndex = thisColumnGroup.nextRowIndex;
        for (ImportColumn column2 : thisColumnGroup.columns.values()) {
            nextRowIndex = Math.max(nextRowIndex, column2.nextRowIndex);
        }
        for (ImportColumnGroup columnGroup2 : thisColumnGroup.subgroups.values()) {
            nextRowIndex = Math.max(nextRowIndex, columnGroup2.nextRowIndex);
        }
        thisColumnGroup.nextRowIndex = nextRowIndex;
    }

    static protected void addCell(
        Project project,
        ImportColumnGroup columnGroup,
        ImportRecord record,
        String columnLocalName,
        String text
    ) {
        if (text == null || ((String) text).isEmpty()) {
            return;
        }
        
        Serializable value = ImporterUtilities.parseCellValue(text);
        
        ImportColumn column = getColumn(project, columnGroup, columnLocalName);
        int cellIndex = column.cellIndex;
        
        int rowIndex = Math.max(columnGroup.nextRowIndex, column.nextRowIndex);
        while (rowIndex >= record.rows.size()) {
            record.rows.add(new ArrayList<Cell>());
        }
        
        List<Cell> row = record.rows.get(rowIndex);
        while (cellIndex >= row.size()) {
            row.add(null);
        }
        
        logger.trace("Adding cell with value : \"" + value + "\" to row : " + rowIndex + " at cell index : " + (cellIndex-1));
        
        row.set(cellIndex, new Cell(value, null));
        
        column.nextRowIndex = rowIndex + 1;
        column.nonBlankCount++;
    }

    static protected ImportColumn getColumn(
        Project project,
        ImportColumnGroup columnGroup,
        String localName
    ) {
        if (columnGroup.columns.containsKey(localName)) {
            return columnGroup.columns.get(localName);
        }

        ImportColumn column = createColumn(project, columnGroup, localName);
        columnGroup.columns.put(localName, column);

        return column;
    }

    static protected ImportColumn createColumn(
        Project project,
        ImportColumnGroup columnGroup,
        String localName
    ) {
        ImportColumn newColumn = new ImportColumn();

        newColumn.name =
            columnGroup.name.length() == 0 ?
            (localName == null ? "Text" : localName) :
            (localName == null ? columnGroup.name : (columnGroup.name + " - " + localName));

        newColumn.cellIndex = project.columnModel.allocateNewCellIndex();
        newColumn.nextRowIndex = columnGroup.nextRowIndex;
        
        return newColumn;
    }

    static protected ImportColumnGroup getColumnGroup(
        Project project,
        ImportColumnGroup columnGroup,
        String localName
    ) {
        if (columnGroup.subgroups.containsKey(localName)) {
            return columnGroup.subgroups.get(localName);
        }

        ImportColumnGroup subgroup = createColumnGroup(project, columnGroup, localName);
        columnGroup.subgroups.put(localName, subgroup);

        return subgroup;
    }

    static protected ImportColumnGroup createColumnGroup(
        Project project,
        ImportColumnGroup columnGroup,
        String localName
    ) {
        ImportColumnGroup newGroup = new ImportColumnGroup();

        newGroup.name =
            columnGroup.name.length() == 0 ?
            (localName == null ? "Text" : localName) :
            (localName == null ? columnGroup.name : (columnGroup.name + " - " + localName));

        newGroup.nextRowIndex = columnGroup.nextRowIndex;
        
        return newGroup;
    }
}