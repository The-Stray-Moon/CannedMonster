package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyLinksTest {
    @Test
    void sharesOnlyBoundBodiesAndExplicitCommonData() {
        Object host = new Object();
        Object facet = new Object();
        Object hostData = new Object();
        Object facetData = new Object();
        Object commonAccessor = new Object();
        Object speciesAccessor = new Object();
        BodyLinks.shareAccessor(commonAccessor);
        assertSame(facet, BodyLinks.body(facet));
        BodyLinks.bind(facet, host, facetData, hostData);
        try {
            assertSame(host, BodyLinks.body(facet));
            assertSame(host, BodyLinks.body(host));
            assertSame(hostData, BodyLinks.data(facetData, commonAccessor));
            assertSame(facetData, BodyLinks.data(facetData, speciesAccessor));
            assertTrue(BodyLinks.isFacet(facet));
            assertFalse(BodyLinks.isFacet(host));
        } finally {
            BodyLinks.unbind(facet, facetData);
        }
        assertSame(facet, BodyLinks.body(facet));
        assertSame(facetData, BodyLinks.data(facetData, commonAccessor));
    }

    @Test
    void rejectsSelfBindingCyclesAndRebinding() {
        Object host = new Object();
        Object facet = new Object();
        Object data = new Object();
        assertThrows(IllegalArgumentException.class, () -> BodyLinks.bind(host, host, data, data));
        BodyLinks.bind(facet, host, data, new Object());
        try {
            assertThrows(IllegalArgumentException.class, () -> BodyLinks.bind(host, facet, new Object(), data));
            assertThrows(IllegalArgumentException.class, () -> BodyLinks.bind(host, new Object(), new Object(), new Object()));
            assertThrows(IllegalArgumentException.class, () -> BodyLinks.bind(facet, new Object(), data, new Object()));
        } finally {
            BodyLinks.unbind(facet, data);
        }
    }

    @Test
    void canonicalIdentityExcludesAllMembersButNotUnrelatedObjects() {
        Object host = new Object();
        Object first = new Object();
        Object second = new Object();
        Object firstData = new Object();
        Object secondData = new Object();
        BodyLinks.bind(first, host, firstData, new Object());
        BodyLinks.bind(second, host, secondData, new Object());
        try {
            assertTrue(BodyLinks.same(first, host));
            assertTrue(BodyLinks.same(second, first));
            assertTrue(BodyLinks.same(host, second));
            assertFalse(BodyLinks.same(first, new Object()));
            assertFalse(BodyLinks.same(first, null));
            assertFalse(BodyLinks.same(null, null));
            assertNull(BodyLinks.body(null));
        } finally {
            BodyLinks.unbind(first, firstData);
            BodyLinks.unbind(second, secondData);
        }
        assertFalse(BodyLinks.same(first, host));
    }

    @Test
    void bindingUsesIdentityRatherThanEntityEquality() {
        Object first = new String("same");
        Object second = new String("same");
        Object host = new Object();
        Object data = new Object();
        BodyLinks.bind(first, host, data, new Object());
        try {
            assertSame(host, BodyLinks.body(first));
            assertSame(second, BodyLinks.body(second));
        } finally {
            BodyLinks.unbind(first, data);
        }
    }
}
