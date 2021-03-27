package de.symeda.sormas.ui.iris;

import com.vaadin.icons.VaadinIcons;

import com.vaadin.server.BrowserWindowOpener;
import com.vaadin.server.ExternalResource;
import com.vaadin.ui.*;
import com.vaadin.ui.themes.ValoTheme;

import de.symeda.sormas.api.FacadeProvider;
import de.symeda.sormas.api.ReferenceDto;
import de.symeda.sormas.ui.utils.CssStyles;

public class IrisListComponent extends VerticalLayout {

	public IrisListComponent(IrisContext context, ReferenceDto entityRef) {
		setWidth(100, Unit.PERCENTAGE);
		setMargin(false);
		setSpacing(false);

		HorizontalLayout componentHeader = new HorizontalLayout();
		componentHeader.setMargin(false);
		componentHeader.setSpacing(false);
		componentHeader.setWidth(100, Unit.PERCENTAGE);
		addComponent(componentHeader);

		Label irisHeader = new Label("IRIS");
		irisHeader.addStyleName(CssStyles.H3);
		componentHeader.addComponent(irisHeader);

		// build button
		Button viewBtn = new Button("Abfragen");
		viewBtn.setIcon(VaadinIcons.DATABASE);
		viewBtn.setStyleName(ValoTheme.BUTTON_PRIMARY);

		String irisUrl = FacadeProvider.getConfigFacade().getIrisServerUrl();
		String ctx = context.toString().toLowerCase();

		// build URL
		String sourceURL = irisUrl + "/" + ctx + "/" + entityRef.getUuid();

		BrowserWindowOpener opener = new BrowserWindowOpener(new ExternalResource(sourceURL));

		// open in new tab
		opener.setWindowName("_blank");
		opener.extend(viewBtn);

		componentHeader.addComponent(viewBtn);
		componentHeader.setComponentAlignment(viewBtn, Alignment.MIDDLE_RIGHT);

	}
}
