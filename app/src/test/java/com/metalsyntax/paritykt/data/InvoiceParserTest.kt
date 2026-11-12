package com.metalsyntax.paritykt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvoiceParserTest {
    @Test fun amounts() {
        assertEquals(1234.56, InvoiceParser.amount("1.234,56")!!, 1e-9)
        assertEquals(1234.56, InvoiceParser.amount("1,234.56")!!, 1e-9)
        assertEquals(1234.0, InvoiceParser.amount("1.234")!!, 1e-9)
        assertEquals(12.5, InvoiceParser.amount("12,5")!!, 1e-9)
        assertNull(InvoiceParser.amount("abc"))
    }

    @Test fun venezuelanFiscalTicket() {
        val inv = InvoiceParser.parse(listOf(
            "FARMATODO C.A.", "RIF J-00012345-6", "FACTURA: 00012345", "FECHA: 05/11/2026 HORA: 14:32",
            "2 x 45,00", "HARINA PAN 1KG (G)  Bs 90,00",
            "ACETAMINOFEN 500MG (E) 120,50",
            "3 JABON AZUL 30,00",
            "SUBTOTAL Bs 240,50", "BI G16,00% 90,00", "IVA G16,00% 14,40", "TOTAL Bs 254,90",
            "EFECTIVO Bs 300,00", "CAMBIO 45,10",
        ))
        assertEquals("FARMATODO C.A.", inv.merchant)
        assertEquals("2026-11-05", inv.invoiceDate)
        assertEquals(Currency.VES, inv.currency)
        assertEquals(3, inv.items.size)
        assertEquals(InvoiceItem("HARINA PAN 1KG", 2.0, 45.0, 90.0), inv.items[0])
        assertEquals("ACETAMINOFEN 500MG", inv.items[1].description)
        assertEquals(3.0, inv.items[2].quantity!!, 1e-9); assertEquals(10.0, inv.items[2].unitPrice!!, 1e-9)
        assertEquals(240.5, inv.subtotal!!, 1e-9); assertEquals(14.4, inv.tax!!, 1e-9); assertEquals(254.9, inv.total!!, 1e-9)
    }

    @Test fun noTotalFallsBackToItemSum() {
        val inv = InvoiceParser.parse(listOf("Cafe Luz", "Latte $3.50", "Croissant $2.25"))
        assertEquals(Currency.USD, inv.currency)
        assertEquals(5.75, inv.total!!, 1e-9)
        assertEquals("Cafe Luz", inv.merchant)
    }

    @Test fun jsonRoundTrip() {
        val inv = InvoiceParser.parse(listOf("Tienda", "Pan 2,00", "TOTAL 2,00"))
        assertEquals(inv, InvoiceData.from(inv.toJson()))
    }
}
