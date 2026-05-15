/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2013 jOpenDocument, by ILM Informatique. All rights reserved.
 *
 * The contents of this file are subject to the terms of the GNU
 * General Public License Version 3 only ("GPL").
 * You may not use this file except in compliance with the License.
 * You can obtain a copy of the License at http://www.gnu.org/licenses/gpl-3.0.html
 * See the License for the specific language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each file.
 *
 */

package org.jopendocument.dom.spreadsheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.jopendocument.dom.ODPackage;
import org.jopendocument.util.Tuple3;

import org.jdom.Namespace;
import org.junit.jupiter.api.Test;

public class CellTest {

    protected Sheet loadRealSheet() throws Exception {
        return ODPackage.createFromStream(this.getClass().getResourceAsStream("test.fods"), null).getSpreadSheet().getSheet(0);
    }

    @Test
    public void testFormulas() throws Exception {
        final Sheet sheet = loadRealSheet().getSpreadSheet().getSheet(1);
        assertEquals("=a0", sheet.getCellAt("B3").getFormula());
        assertEquals(null, sheet.getCellAt("C3").getFormula());

        final Namespace defaultNS = sheet.getODDocument().getVersion().getNS("of");
        final MutableCell<SpreadSheet> a1Cell = sheet.getCellAt("A1");

        final String formula = "=SUM([.A2:.A3])";
        a1Cell.setFormula(formula);
        assertEquals(formula, a1Cell.getFormula());
        assertEquals(formula, a1Cell.getRawFormula());
        assertEquals(Tuple3.create(defaultNS, formula, null), a1Cell.getFormulaAndNamespace());

        // as in LO, setting a cell value clears the formula
        a1Cell.setValue("foo");
        assertEquals(null, a1Cell.getFormula());

        a1Cell.setFormulaAndNamespace(defaultNS, formula);
        // getFormula() returns the same
        assertEquals(formula, a1Cell.getFormula());
        // but the raw value has the prefix
        assertEquals("of:" + formula, a1Cell.getRawFormula());
        assertEquals(Tuple3.create(defaultNS, formula, defaultNS.getPrefix()), a1Cell.getFormulaAndNamespace());

        // ambiguity
        final String ambuiguateFormula = "foo:bar";
        a1Cell.setFormulaAndNamespace(null, ambuiguateFormula);
        // the formula is correctly returned
        assertEquals(ambuiguateFormula, a1Cell.getFormula());
        // but the raw value has the prefix to disambiguate
        assertEquals("of:" + ambuiguateFormula, a1Cell.getRawFormula());

        // non default namespace
        final Namespace nonDefNS = Namespace.getNamespace("nonDef", "urn:myFormula");
        a1Cell.setFormulaAndNamespace(nonDefNS, ambuiguateFormula);
        assertEquals("nonDef:" + ambuiguateFormula, a1Cell.getRawFormula());
        assertEquals(Tuple3.create(nonDefNS, ambuiguateFormula, nonDefNS.getPrefix()), a1Cell.getFormulaAndNamespace());
        try {
            a1Cell.getFormula();
            fail("Not default namespace");
        } catch (Exception e) {
            // OK
        }
        a1Cell.setFormula(null);
        assertEquals(null, a1Cell.getFormula());
    }

}
