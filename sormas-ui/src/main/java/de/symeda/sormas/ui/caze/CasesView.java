/*******************************************************************************
 * SORMAS® - Surveillance Outbreak Response Management & Analysis System
 * Copyright © 2016-2018 Helmholtz-Zentrum für Infektionsforschung GmbH (HZI)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package de.symeda.sormas.ui.caze;

import java.util.Date;
import java.util.HashMap;

import org.vaadin.hene.popupbutton.PopupButton;

import com.vaadin.icons.VaadinIcons;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.Page;
import com.vaadin.server.StreamResource;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.MenuBar.Command;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.ValoTheme;
import com.vaadin.v7.ui.CheckBox;
import com.vaadin.v7.ui.ComboBox;
import com.vaadin.v7.ui.TextField;

import de.symeda.sormas.api.Disease;
import de.symeda.sormas.api.FacadeProvider;
import de.symeda.sormas.api.caze.CaseClassification;
import de.symeda.sormas.api.caze.CaseCriteria;
import de.symeda.sormas.api.caze.CaseDataDto;
import de.symeda.sormas.api.caze.CaseExportDto;
import de.symeda.sormas.api.caze.CaseOutcome;
import de.symeda.sormas.api.caze.InvestigationStatus;
import de.symeda.sormas.api.caze.NewCaseDateType;
import de.symeda.sormas.api.epidata.EpiDataDto;
import de.symeda.sormas.api.facility.FacilityReferenceDto;
import de.symeda.sormas.api.hospitalization.HospitalizationDto;
import de.symeda.sormas.api.i18n.Captions;
import de.symeda.sormas.api.i18n.Descriptions;
import de.symeda.sormas.api.i18n.I18nProperties;
import de.symeda.sormas.api.i18n.Strings;
import de.symeda.sormas.api.person.PersonDto;
import de.symeda.sormas.api.person.PresentCondition;
import de.symeda.sormas.api.region.DistrictReferenceDto;
import de.symeda.sormas.api.region.RegionReferenceDto;
import de.symeda.sormas.api.symptoms.SymptomsDto;
import de.symeda.sormas.api.user.UserDto;
import de.symeda.sormas.api.user.UserReferenceDto;
import de.symeda.sormas.api.user.UserRight;
import de.symeda.sormas.api.user.UserRole;
import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.api.utils.EpiWeek;
import de.symeda.sormas.ui.ControllerProvider;
import de.symeda.sormas.ui.UserProvider;
import de.symeda.sormas.ui.ViewModelProviders;
import de.symeda.sormas.ui.dashboard.DateFilterOption;
import de.symeda.sormas.ui.utils.AbstractView;
import de.symeda.sormas.ui.utils.CssStyles;
import de.symeda.sormas.ui.utils.DownloadUtil;
import de.symeda.sormas.ui.utils.EpiWeekAndDateFilterComponent;
import de.symeda.sormas.ui.utils.FieldHelper;
import de.symeda.sormas.ui.utils.LayoutUtil;
import de.symeda.sormas.ui.utils.VaadinUiUtil;

/**
 * A view for performing create-read-update-delete operations on products.
 *
 * See also {@link CaseController} for fetching the data, the actual CRUD
 * operations and controlling the view based on events from outside.
 */
public class CasesView extends AbstractView {

	private static final long serialVersionUID = -3533557348144005469L;

	public static final String VIEW_NAME = "cases";

	private CaseCriteria criteria;
	
	private CaseGrid grid;    
	private Button createButton;
	private HashMap<Button, String> statusButtons;
	private Button activeStatusButton;

	private VerticalLayout gridLayout;
	private HorizontalLayout firstFilterRowLayout;
	private HorizontalLayout secondFilterRowLayout;
	private HorizontalLayout dateFilterRowLayout;

	private String originalViewTitle;

	// Filters
	private ComboBox outcomeFilter;
	private ComboBox diseaseFilter;
	private ComboBox classificationFilter;
	private TextField searchField;
	private ComboBox presentConditionFilter;
	private ComboBox regionFilter;
	private ComboBox districtFilter;
	private ComboBox facilityFilter;
	private ComboBox officerFilter;
	private ComboBox reportedByFilter;
	private CheckBox casesWithoutGeoCoordsFilter;
	private EpiWeekAndDateFilterComponent<NewCaseDateType> weekAndDateFilter;

	// Bulk operations
	private MenuItem archiveItem;
	private MenuItem dearchiveItem;

	private Button switchArchivedActiveButton;

	private Button resetButton;
	private Button expandFiltersButton;
	private Button collapseFiltersButton;

	public CasesView() {
		super(VIEW_NAME);

		originalViewTitle = getViewTitleLabel().getValue();
		
		criteria = ViewModelProviders.of(CasesView.class).get(CaseCriteria.class);
		if (criteria.getArchived() == null) {
			criteria.archived(false);
		}
		
		grid = new CaseGrid();
		grid.setCriteria(criteria);
		gridLayout = new VerticalLayout();
		gridLayout.addComponent(createFilterBar());
		gridLayout.addComponent(createStatusFilterBar());
		gridLayout.addComponent(grid);
		gridLayout.setMargin(true);
		gridLayout.setSpacing(false);
		gridLayout.setSizeFull();
		gridLayout.setExpandRatio(grid, 1);
		gridLayout.setStyleName("crud-main-layout");

		grid.addItemCountChangedListener(e -> updateStatusButtons());

		if (UserProvider.getCurrent().hasUserRight(UserRight.CASE_IMPORT)) {
			Button importButton = new Button(I18nProperties.getCaption(Captions.actionImport));
			importButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
			importButton.setIcon(VaadinIcons.UPLOAD);
			importButton.addClickListener(e -> {
				Window popupWindow = VaadinUiUtil.showPopupWindow(new CaseImportLayout());
				popupWindow.setCaption(I18nProperties.getString(Strings.headingImportCases));
				popupWindow.addCloseListener(c -> {
					grid.reload();
				});
			});
			addHeaderComponent(importButton);
		}

		if (UserProvider.getCurrent().hasUserRight(UserRight.CASE_EXPORT)) {
			PopupButton exportButton = new PopupButton(I18nProperties.getCaption(Captions.export)); 
			exportButton.setIcon(VaadinIcons.DOWNLOAD);
			VerticalLayout exportLayout = new VerticalLayout();
			exportLayout.setSpacing(true); 
			exportLayout.setMargin(true);
			exportLayout.addStyleName(CssStyles.LAYOUT_MINIMAL);
			exportLayout.setWidth(200, Unit.PIXELS);
			exportButton.setContent(exportLayout);
			addHeaderComponent(exportButton);

			Button basicExportButton = new Button(I18nProperties.getCaption(Captions.exportBasic));
			basicExportButton.setDescription(I18nProperties.getString(Strings.infoBasicExport));
			basicExportButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
			basicExportButton.setIcon(VaadinIcons.TABLE);
			basicExportButton.setWidth(100, Unit.PERCENTAGE);
			exportLayout.addComponent(basicExportButton);

			// TODO #1012
//			StreamResource streamResource = DownloadUtil.createGridExportStreamResource(
//					grid.getDataProvider().fetch(query)grid.getContainerDataSource(), grid.getColumns(), "sormas_cases", "sormas_cases_" + DateHelper.formatDateForExport(new Date()) + ".csv");
//			FileDownloader fileDownloader = new FileDownloader(streamResource);
//			fileDownloader.extend(basicExportButton);

			Button extendedExportButton = new Button(I18nProperties.getCaption(Captions.exportDetailed));
			extendedExportButton.setDescription(I18nProperties.getString(Strings.infoDetailedExport));
			extendedExportButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
			extendedExportButton.setIcon(VaadinIcons.FILE_TEXT);
			extendedExportButton.setWidth(100, Unit.PERCENTAGE);
			exportLayout.addComponent(extendedExportButton);

			StreamResource extendedExportStreamResource = DownloadUtil.createCsvExportStreamResource(CaseExportDto.class,
					(Integer start, Integer max) -> FacadeProvider.getCaseFacade().getExportList(UserProvider.getCurrent().getUuid(), grid.getCriteria(), start, max), 
					(propertyId,type) -> {
						String caption = I18nProperties.getPrefixCaption(CaseExportDto.I18N_PREFIX, propertyId,
								I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, propertyId,
										I18nProperties.getPrefixCaption(PersonDto.I18N_PREFIX, propertyId,
												I18nProperties.getPrefixCaption(SymptomsDto.I18N_PREFIX, propertyId,
														I18nProperties.getPrefixCaption(EpiDataDto.I18N_PREFIX, propertyId,
																I18nProperties.getPrefixCaption(HospitalizationDto.I18N_PREFIX, propertyId))))));
						if (Date.class.isAssignableFrom(type)) {
							caption += " (" + DateHelper.getLocalShortDatePattern() + ")";
						}
						return caption;
					},
					"sormas_cases_" + DateHelper.formatDateForExport(new Date()) + ".csv");
			new FileDownloader(extendedExportStreamResource).extend(extendedExportButton);

			// Warning if no filters have been selected
			Label warningLabel = new Label(I18nProperties.getString(Strings.infoExportNoFilters), ContentMode.HTML);
			warningLabel.setWidth(100, Unit.PERCENTAGE);
			exportLayout.addComponent(warningLabel);
			warningLabel.setVisible(false);

			exportButton.addClickListener(e -> {
				warningLabel.setVisible(!criteria.hasAnyFilterActive());
			});
		}

		if (UserProvider.getCurrent().hasUserRight(UserRight.CASE_CREATE)) {
			createButton = new Button(I18nProperties.getCaption(Captions.caseNewCase));
			createButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
			createButton.setIcon(VaadinIcons.PLUS_CIRCLE);
			createButton.addClickListener(e -> ControllerProvider.getCaseController().create());
			addHeaderComponent(createButton);
		}

		addComponent(gridLayout);
	}

	public VerticalLayout createFilterBar() {
		VerticalLayout filterLayout = new VerticalLayout();
		filterLayout.setSpacing(false);
		filterLayout.setMargin(false);
		filterLayout.setWidth(100, Unit.PERCENTAGE);

		firstFilterRowLayout = new HorizontalLayout();
		firstFilterRowLayout.setMargin(false);
		firstFilterRowLayout.setSpacing(true);
		firstFilterRowLayout.setSizeUndefined();
		{
			outcomeFilter = new ComboBox();
			outcomeFilter.setWidth(140, Unit.PIXELS);
			outcomeFilter.setInputPrompt(I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, CaseDataDto.OUTCOME));
			outcomeFilter.addItems((Object[]) CaseOutcome.values());
			outcomeFilter.addValueChangeListener(e -> {
				criteria.outcome(((CaseOutcome) e.getProperty().getValue()));
				navigateTo(criteria);
			});
			firstFilterRowLayout.addComponent(outcomeFilter);

			diseaseFilter = new ComboBox();
			diseaseFilter.setWidth(140, Unit.PIXELS);
			diseaseFilter.setInputPrompt(I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, CaseDataDto.DISEASE));
			diseaseFilter.addItems((Object[])Disease.values());
			diseaseFilter.addValueChangeListener(e -> {
				criteria.disease(((Disease)e.getProperty().getValue()));
				navigateTo(criteria);
			});
			firstFilterRowLayout.addComponent(diseaseFilter);

			classificationFilter = new ComboBox();
			classificationFilter.setWidth(140, Unit.PIXELS);
			classificationFilter.setInputPrompt(I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, CaseDataDto.CASE_CLASSIFICATION));
			classificationFilter.addItems((Object[])CaseClassification.values());
			classificationFilter.addValueChangeListener(e -> {
				criteria.caseClassification(((CaseClassification)e.getProperty().getValue()));
				navigateTo(criteria);
			});
			firstFilterRowLayout.addComponent(classificationFilter);

			searchField = new TextField();
			searchField.setWidth(200, Unit.PIXELS);
			searchField.setNullRepresentation("");
			searchField.setInputPrompt(I18nProperties.getString(Strings.promptCasesSearchField));
			searchField.addTextChangeListener(e -> {
				criteria.nameUuidEpidNumberLike(e.getText());
				grid.reload();
			});
			firstFilterRowLayout.addComponent(searchField);

			addShowMoreOrLessFiltersButtons(firstFilterRowLayout);
			
			resetButton = new Button(I18nProperties.getCaption(Captions.actionResetFilters));
			resetButton.setVisible(false);
			resetButton.addClickListener(event -> {
				ViewModelProviders.of(CasesView.class).remove(CaseCriteria.class);
				navigateTo(null);
			});
			firstFilterRowLayout.addComponent(resetButton);
		}
		filterLayout.addComponent(firstFilterRowLayout);

		secondFilterRowLayout = new HorizontalLayout();
		secondFilterRowLayout.setMargin(false);
		secondFilterRowLayout.setSpacing(true);
		secondFilterRowLayout.setSizeUndefined();
		{
			presentConditionFilter = new ComboBox();
			presentConditionFilter.setWidth(140, Unit.PIXELS);
			presentConditionFilter.setInputPrompt(I18nProperties.getPrefixCaption(PersonDto.I18N_PREFIX, PersonDto.PRESENT_CONDITION));
			presentConditionFilter.addItems((Object[])PresentCondition.values());
			presentConditionFilter.addValueChangeListener(e -> {
				criteria.presentCondition(((PresentCondition)e.getProperty().getValue()));
				navigateTo(criteria);
			});
			secondFilterRowLayout.addComponent(presentConditionFilter);      

			UserDto user = UserProvider.getCurrent().getUser();

			regionFilter = new ComboBox();
			if (user.getRegion() == null) {
				regionFilter.setWidth(140, Unit.PIXELS);
				regionFilter.setInputPrompt(I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, CaseDataDto.REGION));
				regionFilter.addItems(FacadeProvider.getRegionFacade().getAllAsReference());
				regionFilter.addValueChangeListener(e -> {
					RegionReferenceDto region = (RegionReferenceDto)e.getProperty().getValue();
					criteria.region(region);
					navigateTo(criteria);
				});
				secondFilterRowLayout.addComponent(regionFilter);
			}

			districtFilter = new ComboBox();
			districtFilter.setWidth(140, Unit.PIXELS);
			districtFilter.setInputPrompt(I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, CaseDataDto.DISTRICT));
			districtFilter.setDescription(I18nProperties.getDescription(Descriptions.descDistrictFilter));
			districtFilter.addValueChangeListener(e -> {
				DistrictReferenceDto district = (DistrictReferenceDto)e.getProperty().getValue();
				criteria.district(district);
				navigateTo(criteria);
			});

			if (user.getRegion() != null && user.getDistrict() == null) {
				districtFilter.addItems(FacadeProvider.getDistrictFacade().getAllByRegion(user.getRegion().getUuid()));
				districtFilter.setEnabled(true);
			} else {
				regionFilter.addValueChangeListener(e -> {
					RegionReferenceDto region = (RegionReferenceDto)e.getProperty().getValue();
					districtFilter.removeAllItems();
					if (region != null) {
						districtFilter.addItems(FacadeProvider.getDistrictFacade().getAllByRegion(region.getUuid()));
						districtFilter.setEnabled(true);
					} else {
						districtFilter.setEnabled(false);
					}
				});
				districtFilter.setEnabled(false);
			}
			secondFilterRowLayout.addComponent(districtFilter);

			facilityFilter = new ComboBox();
			facilityFilter.setWidth(140, Unit.PIXELS);
			facilityFilter.setInputPrompt(I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, CaseDataDto.HEALTH_FACILITY));
			facilityFilter.setDescription(I18nProperties.getDescription(Descriptions.descFacilityFilter));
			facilityFilter.addValueChangeListener(e -> {
				criteria.healthFacility(((FacilityReferenceDto)e.getProperty().getValue()));
				navigateTo(criteria);
			});
			facilityFilter.setEnabled(false);
			secondFilterRowLayout.addComponent(facilityFilter);

			districtFilter.addValueChangeListener(e-> {
				facilityFilter.removeAllItems();
				DistrictReferenceDto district = (DistrictReferenceDto)e.getProperty().getValue();
				if (district != null) {
					facilityFilter.addItems(FacadeProvider.getFacilityFacade().getHealthFacilitiesByDistrict(district, true));
					facilityFilter.setEnabled(true);
				} else {
					facilityFilter.setEnabled(false);
				}
			});

			officerFilter = new ComboBox();
			officerFilter.setWidth(140, Unit.PIXELS);
			officerFilter.setInputPrompt(I18nProperties.getPrefixCaption(CaseDataDto.I18N_PREFIX, CaseDataDto.SURVEILLANCE_OFFICER));
			if (user.getRegion() != null) {
				officerFilter.addItems(FacadeProvider.getUserFacade().getUsersByRegionAndRoles(user.getRegion(), UserRole.SURVEILLANCE_OFFICER));
			}
			officerFilter.addValueChangeListener(e -> {
				criteria.surveillanceOfficer(((UserReferenceDto)e.getProperty().getValue()));
				navigateTo(criteria);
			});
			secondFilterRowLayout.addComponent(officerFilter);

			reportedByFilter = new ComboBox();
			reportedByFilter.setWidth(140, Unit.PIXELS);
			reportedByFilter.setInputPrompt(I18nProperties.getString(Strings.reportedBy));
			reportedByFilter.addItems((Object[]) UserRole.values());
			reportedByFilter.addValueChangeListener(e -> {
				criteria.reportingUserRole((UserRole) e.getProperty().getValue());
				navigateTo(criteria);
			});
			secondFilterRowLayout.addComponent(reportedByFilter);

			casesWithoutGeoCoordsFilter = new CheckBox();
			CssStyles.style(casesWithoutGeoCoordsFilter, CssStyles.CHECKBOX_FILTER_INLINE);
			casesWithoutGeoCoordsFilter.setCaption(I18nProperties.getCaption(Captions.caseFilterWithoutGeo));
			casesWithoutGeoCoordsFilter.setDescription(I18nProperties.getDescription(Descriptions.descCaseFilterWithoutGeo));
			casesWithoutGeoCoordsFilter.addValueChangeListener(e -> {
				criteria.mustHaveNoGeoCoordinates((Boolean) e.getProperty().getValue());
				navigateTo(criteria);
			});
			secondFilterRowLayout.addComponent(casesWithoutGeoCoordsFilter);
		}
		filterLayout.addComponent(secondFilterRowLayout);
		secondFilterRowLayout.setVisible(false);

		dateFilterRowLayout = new HorizontalLayout();
		dateFilterRowLayout.setSpacing(true);
		dateFilterRowLayout.setSizeUndefined();
		{
			Button applyButton = new Button(I18nProperties.getCaption(Captions.actionApplyDateFilter));

			weekAndDateFilter = new EpiWeekAndDateFilterComponent<>(applyButton, false, false, I18nProperties.getString(Strings.infoCaseDate), NewCaseDateType.class, I18nProperties.getString(Strings.promptNewCaseDateType), NewCaseDateType.MOST_RELEVANT);
			weekAndDateFilter.getWeekFromFilter().setInputPrompt(I18nProperties.getString(Strings.promptCasesEpiWeekFrom));
			weekAndDateFilter.getWeekToFilter().setInputPrompt(I18nProperties.getString(Strings.promptCasesEpiWeekTo));
			weekAndDateFilter.getDateFromFilter().setInputPrompt(I18nProperties.getString(Strings.promptCasesDateFrom));
			weekAndDateFilter.getDateToFilter().setInputPrompt(I18nProperties.getString(Strings.promptCasesDateTo));
			dateFilterRowLayout.addComponent(weekAndDateFilter);
			dateFilterRowLayout.addComponent(applyButton);

			applyButton.addClickListener(e -> {
				DateFilterOption dateFilterOption = (DateFilterOption) weekAndDateFilter.getDateFilterOptionFilter().getValue();
				Date fromDate, toDate;
				if (dateFilterOption == DateFilterOption.DATE) {
					fromDate = DateHelper.getStartOfDay(weekAndDateFilter.getDateFromFilter().getValue());
					toDate = DateHelper.getEndOfDay(weekAndDateFilter.getDateToFilter().getValue());
				} else {
					fromDate = DateHelper.getEpiWeekStart((EpiWeek) weekAndDateFilter.getWeekFromFilter().getValue());
					toDate = DateHelper.getEpiWeekEnd((EpiWeek) weekAndDateFilter.getWeekToFilter().getValue());
				}
				if ((fromDate != null && toDate != null) || (fromDate == null && toDate == null)) {
					applyButton.removeStyleName(ValoTheme.BUTTON_PRIMARY);
					NewCaseDateType newCaseDateType = (NewCaseDateType) weekAndDateFilter.getDateTypeSelector().getValue();
					criteria.newCaseDateBetween(fromDate, toDate, newCaseDateType != null ? newCaseDateType : NewCaseDateType.MOST_RELEVANT);
					navigateTo(criteria);
				} else {
					if (dateFilterOption == DateFilterOption.DATE) {
						Notification notification = new Notification(I18nProperties.getString(Strings.headingMissingDateFilter), 
								I18nProperties.getString(Strings.messageMissingDateFilter), Type.WARNING_MESSAGE, false);
						notification.setDelayMsec(-1);
						notification.show(Page.getCurrent());
					} else {
						Notification notification = new Notification(I18nProperties.getString(Strings.headingMissingEpiWeekFilter), 
								I18nProperties.getString(Strings.messageMissingEpiWeekFilter), Type.WARNING_MESSAGE, false);
						notification.setDelayMsec(-1);
						notification.show(Page.getCurrent());
					}
				}
			});
		}
		filterLayout.addComponent(dateFilterRowLayout);
		dateFilterRowLayout.setVisible(false);

		return filterLayout;
	}

	public HorizontalLayout createStatusFilterBar() {
		HorizontalLayout statusFilterLayout = new HorizontalLayout();
		statusFilterLayout.setSpacing(true);
		statusFilterLayout.setMargin(false);
		statusFilterLayout.setWidth(100, Unit.PERCENTAGE);
		statusFilterLayout.addStyleName(CssStyles.VSPACE_3);

		statusButtons = new HashMap<>();

		Button statusAll = new Button(I18nProperties.getCaption(Captions.all), e -> {
			criteria.investigationStatus(null);
			navigateTo(criteria);
		});
		CssStyles.style(statusAll, ValoTheme.BUTTON_BORDERLESS, CssStyles.BUTTON_FILTER);
		statusAll.setCaptionAsHtml(true);
		statusFilterLayout.addComponent(statusAll);
		statusButtons.put(statusAll, I18nProperties.getCaption(Captions.all));
		activeStatusButton = statusAll;

		for (InvestigationStatus status : InvestigationStatus.values()) {
			Button statusButton = new Button(status.toString(), e -> {
				criteria.investigationStatus(status);
				navigateTo(criteria);
			});
			statusButton.setData(status);
			CssStyles.style(statusButton, ValoTheme.BUTTON_BORDERLESS, CssStyles.BUTTON_FILTER, CssStyles.BUTTON_FILTER_LIGHT);
			statusButton.setCaptionAsHtml(true);
			statusFilterLayout.addComponent(statusButton);
			statusButtons.put(statusButton, status.toString());
		}

		HorizontalLayout actionButtonsLayout = new HorizontalLayout();
		actionButtonsLayout.setSpacing(true);
		{
			// Show archived/active cases button
			if (UserProvider.getCurrent().hasUserRight(UserRight.CASE_VIEW_ARCHIVED)) {
				switchArchivedActiveButton = new Button(I18nProperties.getCaption(Captions.caseShowArchived));
				switchArchivedActiveButton.setStyleName(ValoTheme.BUTTON_LINK);
				switchArchivedActiveButton.addClickListener(e -> {
					criteria.archived(Boolean.TRUE.equals(criteria.getArchived()) ? null : Boolean.TRUE);
					navigateTo(criteria);
				});
				actionButtonsLayout.addComponent(switchArchivedActiveButton);
			}

			// Bulk operation dropdown
			if (UserProvider.getCurrent().hasUserRight(UserRight.PERFORM_BULK_OPERATIONS)) {
				MenuBar bulkOperationsDropdown = new MenuBar();	
				MenuItem bulkOperationsItem = bulkOperationsDropdown.addItem(I18nProperties.getCaption(Captions.bulkActions), null);

				Command changeCommand = selectedItem -> {
					ControllerProvider.getCaseController().showBulkCaseDataEditComponent(grid.asMultiSelect().getSelectedItems());
				};
				bulkOperationsItem.addItem(I18nProperties.getCaption(Captions.bulkEdit), VaadinIcons.ELLIPSIS_H, changeCommand);

				Command deleteCommand = selectedItem -> {
					ControllerProvider.getCaseController().deleteAllSelectedItems(grid.asMultiSelect().getSelectedItems(), new Runnable() {
						public void run() {
							grid.reload();
						}
					});
				};
				bulkOperationsItem.addItem(I18nProperties.getCaption(Captions.bulkDelete), VaadinIcons.TRASH, deleteCommand);

				Command archiveCommand = selectedItem -> {
					ControllerProvider.getCaseController().archiveAllSelectedItems(grid.asMultiSelect().getSelectedItems(), new Runnable() {
						public void run() {
							grid.reload();
						}
					});
				};
				archiveItem = bulkOperationsItem.addItem(I18nProperties.getCaption(Captions.actionArchive), VaadinIcons.ARCHIVE, archiveCommand);

				Command dearchiveCommand = selectedItem -> {
					ControllerProvider.getCaseController().dearchiveAllSelectedItems(grid.asMultiSelect().getSelectedItems(), new Runnable() {
						public void run() {
							grid.reload();
						}
					});
				};
				dearchiveItem = bulkOperationsItem.addItem(I18nProperties.getCaption(Captions.actionDearchive), VaadinIcons.ARCHIVE, dearchiveCommand);
				dearchiveItem.setVisible(false);

				actionButtonsLayout.addComponent(bulkOperationsDropdown);
			}
		}
		statusFilterLayout.addComponent(actionButtonsLayout);
		statusFilterLayout.setComponentAlignment(actionButtonsLayout, Alignment.TOP_RIGHT);
		statusFilterLayout.setExpandRatio(actionButtonsLayout, 1);

		return statusFilterLayout;
	}

	private void addShowMoreOrLessFiltersButtons(HorizontalLayout parentLayout) {
		expandFiltersButton = new Button(I18nProperties.getCaption(Captions.actionShowMoreFilters), VaadinIcons.CHEVRON_DOWN);
		CssStyles.style(expandFiltersButton, ValoTheme.BUTTON_BORDERLESS, CssStyles.VSPACE_TOP_NONE, CssStyles.LABEL_PRIMARY);
		collapseFiltersButton = new Button(I18nProperties.getCaption(Captions.actionShowLessFilters), VaadinIcons.CHEVRON_UP);
		CssStyles.style(collapseFiltersButton, ValoTheme.BUTTON_BORDERLESS, CssStyles.VSPACE_TOP_NONE, CssStyles.LABEL_PRIMARY);

		expandFiltersButton.addClickListener(e -> {
			setFiltersExpanded(true);
		});

		collapseFiltersButton.addClickListener(e -> {
			setFiltersExpanded(false);
		});

		parentLayout.addComponent(expandFiltersButton);
		parentLayout.addComponent(collapseFiltersButton);
		parentLayout.setComponentAlignment(expandFiltersButton, Alignment.TOP_LEFT);
		parentLayout.setComponentAlignment(collapseFiltersButton, Alignment.TOP_LEFT);
		collapseFiltersButton.setVisible(false);
	}
	
	public void setFiltersExpanded(boolean expanded) {
		expandFiltersButton.setVisible(!expanded);
		collapseFiltersButton.setVisible(expanded);
		secondFilterRowLayout.setVisible(expanded);
		dateFilterRowLayout.setVisible(expanded);
	}

	@Override
	public void enter(ViewChangeEvent event) {
		String params = event.getParameters().trim();
		if (params.startsWith("?")) {
			params = params.substring(1);
			criteria.fromUrlParams(params);
		}
		updateFilterComponents();
		grid.reload();
	}
	
	public void updateFilterComponents() {
		// TODO replace with Vaadin 8 databinding
		applyingCriteria = true;
		
		resetButton.setVisible(criteria.hasAnyFilterActive());
		
		updateStatusButtons();
		updateArchivedButton();

		outcomeFilter.setValue(criteria.getOutcome());
		diseaseFilter.setValue(criteria.getDisease());
		classificationFilter.setValue(criteria.getCaseClassification());
		searchField.setValue(criteria.getNameUuidEpidNumberLike());
		presentConditionFilter.setValue(criteria.getPresentCondition());
		regionFilter.setValue(criteria.getRegion());
		districtFilter.setValue(criteria.getDistrict());
		facilityFilter.setValue(criteria.getHealthFacility());
		officerFilter.setValue(criteria.getSurveillanceOfficer());
		reportedByFilter.setValue(criteria.getReportingUserRole());
		casesWithoutGeoCoordsFilter.setValue(criteria.isMustHaveNoGeoCoordinates());
		weekAndDateFilter.getDateTypeSelector().setValue(criteria.getNewCaseDateType());
		weekAndDateFilter.getDateFromFilter().setValue(criteria.getNewCaseDateFrom());
		weekAndDateFilter.getDateToFilter().setValue(criteria.getNewCaseDateTo());
		weekAndDateFilter.getDateFilterOptionFilter().setValue(DateFilterOption.DATE);
		
		boolean hasExpandedFilter = FieldHelper.streamFields(secondFilterRowLayout)
				.anyMatch(f -> !f.isEmpty());
		hasExpandedFilter |=  FieldHelper.streamFields(dateFilterRowLayout)
				.filter(f -> f != weekAndDateFilter.getDateFilterOptionFilter())
				.anyMatch(f -> !f.isEmpty());
		if (hasExpandedFilter) {
			setFiltersExpanded(true);
		}		    
		
		applyingCriteria = false;
	}

	public void clearSelection() {
		grid.asMultiSelect().clear();
	}

	private void updateStatusButtons() {
		statusButtons.keySet().forEach(b -> {
			CssStyles.style(b, CssStyles.BUTTON_FILTER_LIGHT);
			b.setCaption(statusButtons.get(b));
			if (b.getData() == criteria.getInvestigationStatus()) {
				activeStatusButton = b;
			}
		});
		CssStyles.removeStyles(activeStatusButton, CssStyles.BUTTON_FILTER_LIGHT);
		if (activeStatusButton != null) {
			activeStatusButton.setCaption(statusButtons.get(activeStatusButton) 
					+ LayoutUtil.spanCss(CssStyles.BADGE, String.valueOf(grid.getItemCount())));
		}
	}

	private void updateArchivedButton() {
		if (switchArchivedActiveButton == null) {
			return;
		}
		
		if (Boolean.TRUE.equals(criteria.getArchived())) {
			getViewTitleLabel().setValue(I18nProperties.getPrefixCaption("View", viewName.replaceAll("/", ".") + ".archive"));
			switchArchivedActiveButton.setCaption(I18nProperties.getCaption(Captions.caseShowActive));
			switchArchivedActiveButton.setStyleName(ValoTheme.BUTTON_PRIMARY);
			if (archiveItem != null && dearchiveItem != null) {
				archiveItem.setVisible(false);
				dearchiveItem.setVisible(true);
			}
		} else {
			getViewTitleLabel().setValue(originalViewTitle);
			switchArchivedActiveButton.setCaption(I18nProperties.getCaption(Captions.caseShowArchived));
			switchArchivedActiveButton.setStyleName(ValoTheme.BUTTON_LINK);
			if (archiveItem != null && dearchiveItem != null) {
				dearchiveItem.setVisible(false);
				archiveItem.setVisible(true);
			}
		} 
	}

}
