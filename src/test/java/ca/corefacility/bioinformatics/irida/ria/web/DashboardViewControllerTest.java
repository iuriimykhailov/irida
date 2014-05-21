package ca.corefacility.bioinformatics.irida.ria.web;

import org.junit.Test;
import org.springframework.mobile.device.site.SitePreference;
import static org.junit.Assert.assertEquals;

/**
 * Unit Tests for {@link DashboardViewController}
 *
 * @author Josh Adam <josh.adam@phac-aspc.gc.ca>
 */
public class DashboardViewControllerTest {
	private DashboardViewController controller = new DashboardViewController();

	@Test
	public void testGetDashboardViewNormal() {
		SitePreference preference = SitePreference.NORMAL;
		assertEquals("views/dashboard", controller.getDashboardView(preference));
	}

	@Test
	public void testGetDashboardViewMobile() {
		SitePreference preference = SitePreference.MOBILE;
		assertEquals("views/dashboard", controller.getDashboardView(preference));
	}
}
