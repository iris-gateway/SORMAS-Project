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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package de.symeda.sormas.backend.person;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import de.symeda.sormas.api.Disease;
import de.symeda.sormas.api.caze.AgeAndBirthDateDto;
import de.symeda.sormas.api.caze.CaseCriteria;
import de.symeda.sormas.api.caze.CaseDataDto;
import de.symeda.sormas.api.caze.CaseOutcome;
import de.symeda.sormas.api.contact.FollowUpStatus;
import de.symeda.sormas.api.contact.FollowUpStatusDto;
import de.symeda.sormas.api.feature.FeatureType;
import de.symeda.sormas.api.i18n.Captions;
import de.symeda.sormas.api.i18n.I18nProperties;
import de.symeda.sormas.api.i18n.Validations;
import de.symeda.sormas.api.location.LocationDto;
import de.symeda.sormas.api.person.ApproximateAgeType;
import de.symeda.sormas.api.person.ApproximateAgeType.ApproximateAgeHelper;
import de.symeda.sormas.api.person.JournalPersonDto;
import de.symeda.sormas.api.person.PersonContactDetailDto;
import de.symeda.sormas.api.person.PersonContactDetailType;
import de.symeda.sormas.api.person.PersonCriteria;
import de.symeda.sormas.api.person.PersonDto;
import de.symeda.sormas.api.person.PersonFacade;
import de.symeda.sormas.api.person.PersonFollowUpEndDto;
import de.symeda.sormas.api.person.PersonHelper;
import de.symeda.sormas.api.person.PersonIndexDto;
import de.symeda.sormas.api.person.PersonNameDto;
import de.symeda.sormas.api.person.PersonReferenceDto;
import de.symeda.sormas.api.person.PersonSimilarityCriteria;
import de.symeda.sormas.api.person.PresentCondition;
import de.symeda.sormas.api.person.SimilarPersonDto;
import de.symeda.sormas.api.person.SymptomJournalStatus;
import de.symeda.sormas.api.region.DistrictReferenceDto;
import de.symeda.sormas.api.user.UserReferenceDto;
import de.symeda.sormas.api.utils.DataHelper;
import de.symeda.sormas.api.utils.DataHelper.Pair;
import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.api.utils.SortProperty;
import de.symeda.sormas.api.utils.ValidationRuntimeException;
import de.symeda.sormas.backend.caze.Case;
import de.symeda.sormas.backend.caze.CaseFacadeEjb.CaseFacadeEjbLocal;
import de.symeda.sormas.backend.caze.CaseQueryContext;
import de.symeda.sormas.backend.caze.CaseService;
import de.symeda.sormas.backend.caze.CaseUserFilterCriteria;
import de.symeda.sormas.backend.common.ConfigFacadeEjb;
import de.symeda.sormas.backend.common.CriteriaBuilderHelper;
import de.symeda.sormas.backend.contact.Contact;
import de.symeda.sormas.backend.contact.ContactService;
import de.symeda.sormas.backend.externaljournal.ExternalJournalService;
import de.symeda.sormas.backend.facility.FacilityFacadeEjb;
import de.symeda.sormas.backend.facility.FacilityService;
import de.symeda.sormas.backend.feature.FeatureConfigurationFacadeEjb.FeatureConfigurationFacadeEjbLocal;
import de.symeda.sormas.backend.location.Location;
import de.symeda.sormas.backend.location.LocationFacadeEjb;
import de.symeda.sormas.backend.location.LocationFacadeEjb.LocationFacadeEjbLocal;
import de.symeda.sormas.backend.region.CommunityFacadeEjb;
import de.symeda.sormas.backend.region.CommunityService;
import de.symeda.sormas.backend.region.CountryFacadeEjb;
import de.symeda.sormas.backend.region.CountryService;
import de.symeda.sormas.backend.region.District;
import de.symeda.sormas.backend.region.DistrictFacadeEjb;
import de.symeda.sormas.backend.region.DistrictService;
import de.symeda.sormas.backend.region.RegionFacadeEjb;
import de.symeda.sormas.backend.region.RegionService;
import de.symeda.sormas.backend.user.User;
import de.symeda.sormas.backend.user.UserFacadeEjb.UserFacadeEjbLocal;
import de.symeda.sormas.backend.user.UserService;
import de.symeda.sormas.backend.util.DtoHelper;
import de.symeda.sormas.backend.util.IterableHelper;
import de.symeda.sormas.backend.util.ModelConstants;
import de.symeda.sormas.backend.util.Pseudonymizer;
import de.symeda.sormas.utils.CaseJoins;

@Stateless(name = "PersonFacade")
public class PersonFacadeEjb implements PersonFacade {

	@PersistenceContext(unitName = ModelConstants.PERSISTENCE_UNIT_NAME)
	private EntityManager em;

	@EJB
	private PersonService personService;
	@EJB
	private CaseService caseService;
	@EJB
	private CaseFacadeEjbLocal caseFacade;
	@EJB
	private ConfigFacadeEjb.ConfigFacadeEjbLocal configFacade;
	@EJB
	private ContactService contactService;
	@EJB
	private FacilityService facilityService;
	@EJB
	private RegionService regionService;
	@EJB
	private DistrictService districtService;
	@EJB
	private CommunityService communityService;
	@EJB
	private LocationFacadeEjbLocal locationFacade;
	@EJB
	private PersonContactDetailService personContactDetailService;
	@EJB
	private UserService userService;
	@EJB
	private ExternalJournalService externalJournalService;
	@EJB
	private CountryService countryService;
	@EJB
	private FeatureConfigurationFacadeEjbLocal featureConfigurationFacade;
	@EJB
	private UserFacadeEjbLocal userFacade;

	@Override
	public List<String> getAllUuids() {
		if (userService.getCurrentUser() == null) {
			return Collections.emptyList();
		}
		return personService.getAllUuids();
	}

	@Override
	public List<PersonNameDto> getMatchingNameDtos(UserReferenceDto userRef, PersonSimilarityCriteria criteria) {

		User user = userService.getByReferenceDto(userRef);
		if (user == null) {
			return Collections.emptyList();
		}

		return new ArrayList<>(personService.getMatchingNameDtos(criteria, null));
	}

	@Override
	public boolean checkMatchingNameInDatabase(UserReferenceDto userRef, PersonSimilarityCriteria criteria) {

		User user = userService.getByReferenceDto(userRef);
		if (user == null) {
			return false;
		}

		return personService.getMatchingNameDtos(criteria, 1).size() > 0;
	}

	@Override
	public List<SimilarPersonDto> getSimilarPersonsByUuids(List<String> personUuids) {
		List<Person> persons = personService.getByUuids(personUuids);
		if (persons == null) {
			return new ArrayList<>();
		} else {
			return persons.stream().map(PersonFacadeEjb::toSimilarPersonDto).collect(Collectors.toList());
		}
	}

	@Override
	public Boolean isValidPersonUuid(String personUuid) {
		return personService.exists(personUuid);
	}

	@Override
	public Map<Disease, Long> getDeathCountByDisease(CaseCriteria caseCriteria, boolean excludeSharedCases, boolean excludeCasesFromContacts) {

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
		Root<Case> root = cq.from(Case.class);

		final CaseQueryContext caseQueryContext = new CaseQueryContext(cb, cq, root);
		CaseJoins<Case> joins = (CaseJoins<Case>) caseQueryContext.getJoins();
		Join<Case, Person> person = joins.getPerson();

		Predicate filter =
			caseService.createUserFilter(cb, cq, root, new CaseUserFilterCriteria().excludeCasesFromContacts(excludeCasesFromContacts));
		filter = CriteriaBuilderHelper.and(cb, filter, caseService.createCriteriaFilter(caseCriteria, caseQueryContext));
		filter = CriteriaBuilderHelper.and(cb, filter, cb.equal(person.get(Person.CAUSE_OF_DEATH_DISEASE), root.get(Case.DISEASE)));

		if (filter != null) {
			cq.where(filter);
		}

		cq.multiselect(person.get(Person.CAUSE_OF_DEATH_DISEASE), cb.count(person));
		cq.groupBy(person.get(Person.CAUSE_OF_DEATH_DISEASE));

		List<Object[]> results = em.createQuery(cq).getResultList();

		Map<Disease, Long> outbreaks = results.stream().collect(Collectors.toMap(e -> (Disease) e[0], e -> (Long) e[1]));

		return outbreaks;
	}

	@Override
	public List<PersonDto> getPersonsAfter(Date date) {
		final User user = userService.getCurrentUser();
		if (user == null) {
			return Collections.emptyList();
		}
		return toPseudonymizedDtos(personService.getAllAfter(date, user));
	}

	@Override
	public List<PersonDto> getByUuids(List<String> uuids) {
		return toPseudonymizedDtos(personService.getByUuids(uuids));
	}

	@Override
	public List<PersonDto> getDeathsBetween(Date fromDate, Date toDate, DistrictReferenceDto districtRef, Disease disease) {
		final User user = userService.getCurrentUser();
		if (user == null) {
			return Collections.emptyList();
		}
		final District district = districtService.getByReferenceDto(districtRef);
		return toPseudonymizedDtos(personService.getDeathsBetween(fromDate, toDate, district, disease, user));
	}

	@Override
	public PersonReferenceDto getReferenceByUuid(String uuid) {
		return Optional.of(uuid).map(u -> personService.getByUuid(u)).map(PersonFacadeEjb::toReferenceDto).orElse(null);
	}

	@Override
	public PersonDto getPersonByUuid(String uuid) {
		final Pseudonymizer pseudonymizer = Pseudonymizer.getDefault(userService::hasRight);
		return Optional.of(uuid)
			.map(u -> personService.getByUuid(u))
			.map(p -> convertToDto(p, pseudonymizer, isPersonInJurisdiction(p)))
			.orElse(null);
	}

	@Override
	public JournalPersonDto getPersonForJournal(String uuid) {
		PersonDto detailedPerson = getPersonByUuid(uuid);
		//only specific attributes of the person shall be returned:
		if (detailedPerson != null) {
			JournalPersonDto exportPerson = new JournalPersonDto();
			exportPerson.setUuid(detailedPerson.getUuid());
			exportPerson.setEmailAddress(detailedPerson.getEmailAddress());
			if (configFacade.getPatientDiaryConfig().isActive()) {
				try {
					PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
					Phonenumber.PhoneNumber numberProto = phoneUtil.parse(detailedPerson.getPhone(), "DE");
					String internationalPhone = phoneUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
					exportPerson.setPhone(internationalPhone);
				} catch (NumberParseException e) {
					exportPerson.setPhone(detailedPerson.getPhone());
				}
			} else {
				exportPerson.setPhone(detailedPerson.getPhone());
			}
			exportPerson.setPseudonymized(detailedPerson.isPseudonymized());
			exportPerson.setFirstName(detailedPerson.getFirstName());
			exportPerson.setLastName(detailedPerson.getLastName());
			exportPerson.setBirthdateYYYY(detailedPerson.getBirthdateYYYY());
			exportPerson.setBirthdateMM(detailedPerson.getBirthdateMM());
			exportPerson.setBirthdateDD(detailedPerson.getBirthdateDD());
			exportPerson.setSex(detailedPerson.getSex());
			exportPerson.setLatestFollowUpEndDate(getLatestFollowUpEndDateByUuid(uuid));
			exportPerson.setFollowUpStatus(getMostRelevantFollowUpStatusByUuid(uuid));
			return exportPerson;
		} else {
			return null;
		}
	}

	@Override
	public PersonDto savePerson(@Valid PersonDto source) throws ValidationRuntimeException {
		return savePerson(source, true);
	}

	public PersonDto savePerson(PersonDto source, boolean checkChangeDate) throws ValidationRuntimeException {
		Person person = personService.getByUuid(source.getUuid());

		PersonDto existingPerson = toDto(person);

		restorePseudonymizedDto(source, person, existingPerson);

		validate(source);

		if (existingPerson != null && existingPerson.isEnrolledInExternalJournal()) {
			externalJournalService.validateExternalJournalPerson(source);
			externalJournalService.handleExternalJournalPersonUpdate(source.toReference());
		}

		person = fillOrBuildEntity(source, person, checkChangeDate);

		personService.ensurePersisted(person);

		onPersonChanged(existingPerson, person);

		return convertToDto(person, Pseudonymizer.getDefault(userService::hasRight), existingPerson == null || isPersonInJurisdiction(person));
	}

	@Override
	public void validate(PersonDto source) throws ValidationRuntimeException {

		if (StringUtils.isEmpty(source.getFirstName())) {
			throw new ValidationRuntimeException(I18nProperties.getValidationError(Validations.specifyFirstName));
		}
		if (StringUtils.isEmpty(source.getLastName())) {
			throw new ValidationRuntimeException(I18nProperties.getValidationError(Validations.specifyLastName));
		}
	}

	//@formatter:off
	@Override
	public List<PersonFollowUpEndDto> getLatestFollowUpEndDates(Date since, boolean forSymptomJournal) {
		Stream<PersonFollowUpEndDto> contactLatestDates = getContactLatestFollowUpEndDates(since, forSymptomJournal);
		boolean caseFollowupEnabled = featureConfigurationFacade.isFeatureEnabled(FeatureType.CASE_FOLLOWUP);
		if (caseFollowupEnabled) {
			Stream<PersonFollowUpEndDto> caseLatestDates = getCaseLatestFollowUpEndDates(since, forSymptomJournal);
			Map<String, Optional<PersonFollowUpEndDto>> latestDates = Stream.concat(contactLatestDates, caseLatestDates)
					.collect(groupingBy(PersonFollowUpEndDto::getPersonUuid,
							maxBy(comparing(PersonFollowUpEndDto::getLatestFollowUpEndDate, Comparator.nullsFirst(Comparator.naturalOrder())))));
			return latestDates.values().stream()
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(Collectors.toList());
		} else {
			return contactLatestDates.collect(Collectors.toList());
		}
	}
	//@formatter:on

	private Stream<PersonFollowUpEndDto> getContactLatestFollowUpEndDates(Date since, boolean forSymptomJournal) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<PersonFollowUpEndDto> cq = cb.createQuery(PersonFollowUpEndDto.class);
		Root<Contact> contactRoot = cq.from(Contact.class);
		Join<Contact, Person> personJoin = contactRoot.join(Contact.PERSON, JoinType.LEFT);

		Predicate filter = contactService.createUserFilter(cb, cq, contactRoot);

		if (since != null) {
			filter = CriteriaBuilderHelper.and(cb, filter, contactService.createChangeDateFilter(cb, contactRoot, since));
		}

		if (forSymptomJournal) {
			filter = CriteriaBuilderHelper.and(cb, filter, cb.equal(personJoin.get(Person.SYMPTOM_JOURNAL_STATUS), SymptomJournalStatus.ACCEPTED));
		}

		if (filter != null) {
			cq.where(filter);
		}

		final Date minDate = new Date(0);
		final Expression<Object> followUpStatusExpression = cb.selectCase()
			.when(cb.equal(contactRoot.get(Contact.FOLLOW_UP_STATUS), FollowUpStatus.CANCELED), cb.nullLiteral(Date.class))
			.otherwise(contactRoot.get(Contact.FOLLOW_UP_UNTIL));
		cq.multiselect(personJoin.get(Person.UUID), followUpStatusExpression);
		cq.orderBy(cb.asc(personJoin.get(Person.UUID)), cb.desc(cb.coalesce(contactRoot.get(Contact.FOLLOW_UP_UNTIL), minDate)));

		return em.createQuery(cq).getResultList().stream().distinct();
	}

	private Stream<PersonFollowUpEndDto> getCaseLatestFollowUpEndDates(Date since, boolean forSymptomJournal) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<PersonFollowUpEndDto> cq = cb.createQuery(PersonFollowUpEndDto.class);
		Root<Case> caseRoot = cq.from(Case.class);
		Join<Case, Person> personJoin = caseRoot.join(Case.PERSON, JoinType.LEFT);

		Predicate filter = caseService.createUserFilter(cb, cq, caseRoot);
		if (since != null) {
			filter = CriteriaBuilderHelper.and(cb, filter, caseService.createChangeDateFilter(cb, caseRoot, since));
		}

		if (forSymptomJournal) {
			filter = CriteriaBuilderHelper.and(cb, filter, cb.equal(personJoin.get(Person.SYMPTOM_JOURNAL_STATUS), SymptomJournalStatus.ACCEPTED));
		}

		if (filter != null) {
			cq.where(filter);
		}

		final Date minDate = new Date(0);
		final Expression<Object> followUpStatusExpression = cb.selectCase()
			.when(cb.equal(caseRoot.get(Case.FOLLOW_UP_STATUS), FollowUpStatus.CANCELED), cb.nullLiteral(Date.class))
			.otherwise(caseRoot.get(Case.FOLLOW_UP_UNTIL));
		cq.multiselect(personJoin.get(Person.UUID), followUpStatusExpression);
		cq.orderBy(cb.asc(personJoin.get(Person.UUID)), cb.desc(cb.coalesce(caseRoot.get(Case.FOLLOW_UP_UNTIL), minDate)));

		return em.createQuery(cq).getResultList().stream().distinct();
	}

	@Override
	public Date getLatestFollowUpEndDateByUuid(String uuid) {
		Date contactLatestDate = getContactLatestFollowUpEndDate(uuid);

		boolean caseFollowupEnabled = featureConfigurationFacade.isFeatureEnabled(FeatureType.CASE_FOLLOWUP);
		if (caseFollowupEnabled) {
			Date caseLatestDate = getCaseLatestFollowUpEndDate(uuid);
			return DateHelper.getLatestDate(contactLatestDate, caseLatestDate);
		} else {
			return contactLatestDate;
		}
	}

	private Date getContactLatestFollowUpEndDate(String uuid) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<PersonFollowUpEndDto> cq = cb.createQuery(PersonFollowUpEndDto.class);
		Root<Contact> contactRoot = cq.from(Contact.class);
		Join<Contact, Person> personJoin = contactRoot.join(Contact.PERSON, JoinType.LEFT);

		Predicate filter = contactService.createUserFilter(cb, cq, contactRoot);
		filter = CriteriaBuilderHelper.and(cb, filter, cb.notEqual(contactRoot.get(Contact.FOLLOW_UP_STATUS), FollowUpStatus.CANCELED));
		filter = CriteriaBuilderHelper.and(cb, filter, cb.notEqual(contactRoot.get(Contact.FOLLOW_UP_STATUS), FollowUpStatus.NO_FOLLOW_UP));

		filter = CriteriaBuilderHelper.and(cb, filter, cb.equal(contactRoot.get(Contact.DELETED), false));

		if (uuid != null) {
			filter = CriteriaBuilderHelper.and(cb, filter, cb.equal(personJoin.get(Person.UUID), uuid));
		}

		if (filter != null) {
			cq.where(filter);
		}

		cq.multiselect(personJoin.get(Person.UUID), contactRoot.get(Contact.FOLLOW_UP_UNTIL));
		cq.orderBy(cb.desc(contactRoot.get(Contact.FOLLOW_UP_UNTIL)));

		List<PersonFollowUpEndDto> results = em.createQuery(cq).getResultList().stream().distinct().collect(Collectors.toList());
		if (results.isEmpty()) {
			return null;
		} else {
			return results.get(0).getLatestFollowUpEndDate();
		}
	}

	private Date getCaseLatestFollowUpEndDate(String uuid) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<PersonFollowUpEndDto> cq = cb.createQuery(PersonFollowUpEndDto.class);
		Root<Case> caseRoot = cq.from(Case.class);
		Join<Case, Person> personJoin = caseRoot.join(Case.PERSON, JoinType.LEFT);

		Predicate filter = caseService.createUserFilter(cb, cq, caseRoot);

		filter = CriteriaBuilderHelper.and(cb, filter, cb.equal(caseRoot.get(Case.DELETED), false));

		if (uuid != null) {
			filter = CriteriaBuilderHelper.and(cb, filter, cb.equal(personJoin.get(Person.UUID), uuid));
		}

		if (filter != null) {
			cq.where(filter);
		}

		cq.multiselect(personJoin.get(Person.UUID), caseRoot.get(Case.FOLLOW_UP_UNTIL));
		cq.orderBy(cb.desc(caseRoot.get(Case.FOLLOW_UP_UNTIL)));

		List<PersonFollowUpEndDto> results = em.createQuery(cq).getResultList().stream().distinct().collect(Collectors.toList());
		if (results.isEmpty()) {
			return null;
		} else {
			return results.get(0).getLatestFollowUpEndDate();
		}
	}

	@Override
	public FollowUpStatus getMostRelevantFollowUpStatusByUuid(String uuid) {

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<FollowUpStatusDto> cq = cb.createQuery(FollowUpStatusDto.class);

		Root<Contact> contactRoot = cq.from(Contact.class);
		Join<Contact, Person> personContactJoin = contactRoot.join(Contact.PERSON, JoinType.LEFT);
		Predicate contactFilter = contactService.createUserFilter(cb, cq, contactRoot);

		contactFilter = CriteriaBuilderHelper.and(cb, contactFilter, cb.equal(contactRoot.get(Contact.DELETED), false));

		if (uuid != null) {
			contactFilter = CriteriaBuilderHelper.and(cb, contactFilter, cb.equal(personContactJoin.get(Person.UUID), uuid));
		}
		if (contactFilter != null) {
			cq.where(contactFilter);
		}
		cq.multiselect(personContactJoin.get(Person.UUID), contactRoot.get(Contact.FOLLOW_UP_STATUS));
		List<FollowUpStatusDto> contactResultList = em.createQuery(cq).getResultList().stream().distinct().collect(Collectors.toList());

		cq = cb.createQuery(FollowUpStatusDto.class);
		Root<Case> caseRoot = cq.from(Case.class);
		Join<Case, Person> personCaseJoin = caseRoot.join(Case.PERSON, JoinType.LEFT);
		Predicate caseFilter = caseService.createUserFilter(cb, cq, caseRoot);

		caseFilter = CriteriaBuilderHelper.and(cb, caseFilter, cb.equal(caseRoot.get(Case.DELETED), false));

		if (uuid != null) {
			caseFilter = CriteriaBuilderHelper.and(cb, caseFilter, cb.equal(personCaseJoin.get(Person.UUID), uuid));

		}
		if (caseFilter != null) {
			cq.where(caseFilter);
		}
		cq.multiselect(personCaseJoin.get(Person.UUID), caseRoot.get(Case.FOLLOW_UP_STATUS));
		List<FollowUpStatusDto> caseResultList = em.createQuery(cq).getResultList().stream().distinct().collect(Collectors.toList());

		List<FollowUpStatusDto> resultList = Stream.concat(contactResultList.stream(), caseResultList.stream()).collect(Collectors.toList());

		if (resultList.isEmpty()) {
			return null;
		} else {
			for (FollowUpStatusDto status : resultList) {
				if (FollowUpStatus.FOLLOW_UP.equals(status.getFollowUpStatus())) {
					return FollowUpStatus.FOLLOW_UP;
				}
			}
			if (listOnlyContainsStatus(resultList, FollowUpStatus.CANCELED)) {
				return FollowUpStatus.CANCELED;
			} else if (listOnlyContainsStatus(resultList, FollowUpStatus.COMPLETED)) {
				return FollowUpStatus.COMPLETED;
			} else if (listOnlyContainsStatus(resultList, FollowUpStatus.LOST)) {
				return FollowUpStatus.LOST;
			} else {
				return FollowUpStatus.NO_FOLLOW_UP;
			}
		}
	}

	private boolean listOnlyContainsStatus(List<FollowUpStatusDto> list, FollowUpStatus parameterStatus) {
		if (list.isEmpty()) {
			return false;
		}
		assert (parameterStatus != null);

		for (FollowUpStatusDto status : list) {
			if (!parameterStatus.equals(status.getFollowUpStatus())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean setSymptomJournalStatus(String personUuid, SymptomJournalStatus status) {
		PersonDto person = getPersonByUuid(personUuid);
		person.setSymptomJournalStatus(status);
		savePerson(person);
		return true;
	}

	public static SimilarPersonDto toSimilarPersonDto(Person entity) {

		Integer approximateAge = entity.getApproximateAge();
		ApproximateAgeType approximateAgeType = entity.getApproximateAgeType();
		if (entity.getBirthdateYYYY() != null) {
			Pair<Integer, ApproximateAgeType> pair = ApproximateAgeHelper
				.getApproximateAge(entity.getBirthdateYYYY(), entity.getBirthdateMM(), entity.getBirthdateDD(), entity.getDeathDate());
			approximateAge = pair.getElement0();
			approximateAgeType = pair.getElement1();
		}

		SimilarPersonDto similarPersonDto = new SimilarPersonDto();
		similarPersonDto.setUuid(entity.getUuid());
		similarPersonDto.setFirstName(entity.getFirstName());
		similarPersonDto.setLastName(entity.getLastName());
		similarPersonDto.setNickname(entity.getNickname());
		similarPersonDto.setAgeAndBirthDate(
			PersonHelper.getAgeAndBirthdateString(
				approximateAge,
				approximateAgeType,
				entity.getBirthdateDD(),
				entity.getBirthdateMM(),
				entity.getBirthdateYYYY(),
				I18nProperties.getUserLanguage()));
		similarPersonDto.setSex(entity.getSex());
		similarPersonDto.setPresentCondition(entity.getPresentCondition());
		similarPersonDto.setPhone(entity.getPhone());
		similarPersonDto.setDistrictName(entity.getAddress().getDistrict() != null ? entity.getAddress().getDistrict().getName() : null);
		similarPersonDto.setCommunityName(entity.getAddress().getCommunity() != null ? entity.getAddress().getCommunity().getName() : null);
		similarPersonDto.setPostalCode(entity.getAddress().getPostalCode());
		similarPersonDto.setCity(entity.getAddress().getCity());
		similarPersonDto.setStreet(entity.getAddress().getStreet());
		similarPersonDto.setHouseNumber(entity.getAddress().getHouseNumber());
		similarPersonDto.setNationalHealthId(entity.getNationalHealthId());
		similarPersonDto.setPassportNumber(entity.getPassportNumber());

		return similarPersonDto;
	}

	public static PersonDto toDto(Person source) {

		if (source == null) {
			return null;
		}

		PersonDto target = new PersonDto();
		DtoHelper.fillDto(target, source);

		target.setFirstName(source.getFirstName());
		target.setLastName(source.getLastName());
		target.setSalutation(source.getSalutation());
		target.setOtherSalutation(source.getOtherSalutation());
		target.setSex(source.getSex());

		target.setPresentCondition(source.getPresentCondition());
		target.setBirthdateDD(source.getBirthdateDD());
		target.setBirthdateMM(source.getBirthdateMM());
		target.setBirthdateYYYY(source.getBirthdateYYYY());

		if (source.getBirthdateYYYY() != null) {

			// calculate the approximate age based on the birth date
			// still not sure whether this is a good solution

			Pair<Integer, ApproximateAgeType> pair = ApproximateAgeHelper
				.getApproximateAge(source.getBirthdateYYYY(), source.getBirthdateMM(), source.getBirthdateDD(), source.getDeathDate());
			target.setApproximateAge(pair.getElement0());
			target.setApproximateAgeType(pair.getElement1());
			target.setApproximateAgeReferenceDate(source.getDeathDate() != null ? source.getDeathDate() : new Date());

		} else {
			target.setApproximateAge(source.getApproximateAge());
			target.setApproximateAgeType(source.getApproximateAgeType());
			target.setApproximateAgeReferenceDate(source.getApproximateAgeReferenceDate());
		}

		target.setCauseOfDeath(source.getCauseOfDeath());
		target.setCauseOfDeathDetails(source.getCauseOfDeathDetails());
		target.setCauseOfDeathDisease(source.getCauseOfDeathDisease());
		target.setDeathDate(source.getDeathDate());
		target.setDeathPlaceType(source.getDeathPlaceType());
		target.setDeathPlaceDescription(source.getDeathPlaceDescription());
		target.setBurialDate(source.getBurialDate());
		target.setBurialPlaceDescription(source.getBurialPlaceDescription());
		target.setBurialConductor(source.getBurialConductor());

		target.setBirthName(source.getBirthName());
		target.setNickname(source.getNickname());
		target.setMothersMaidenName(source.getMothersMaidenName());

		target.setAddress(LocationFacadeEjb.toDto(source.getAddress()));
		List<LocationDto> locations = new ArrayList<>();
		for (Location location : source.getAddresses()) {
			LocationDto locationDto = LocationFacadeEjb.toDto(location);
			locations.add(locationDto);
		}
		target.setAddresses(locations);

		if (!CollectionUtils.isEmpty(source.getPersonContactDetails())) {
			target.setPersonContactDetails(source.getPersonContactDetails().stream().map(entity -> {
				final PersonContactDetailDto personContactDetailDto = PersonContactDetailDto.build(
					source.toReference(),
					entity.isPrimaryContact(),
					entity.getPersonContactDetailType(),
					entity.getPhoneNumberType(),
					entity.getDetails(),
					entity.getContactInformation(),
					entity.getAdditionalInformation(),
					entity.isThirdParty(),
					entity.getThirdPartyRole(),
					entity.getThirdPartyName());
				DtoHelper.fillDto(personContactDetailDto, entity);
				return personContactDetailDto;
			}).collect(Collectors.toList()));
		}

		target.setEducationType(source.getEducationType());
		target.setEducationDetails(source.getEducationDetails());

		target.setOccupationType(source.getOccupationType());
		target.setOccupationDetails(source.getOccupationDetails());
		target.setArmedForcesRelationType(source.getArmedForcesRelationType());

		target.setMothersName(source.getMothersName());
		target.setFathersName(source.getFathersName());
		target.setNamesOfGuardians(source.getNamesOfGuardians());
		target.setPlaceOfBirthRegion(RegionFacadeEjb.toReferenceDto(source.getPlaceOfBirthRegion()));
		target.setPlaceOfBirthDistrict(DistrictFacadeEjb.toReferenceDto(source.getPlaceOfBirthDistrict()));
		target.setPlaceOfBirthCommunity(CommunityFacadeEjb.toReferenceDto(source.getPlaceOfBirthCommunity()));
		target.setPlaceOfBirthFacility(FacilityFacadeEjb.toReferenceDto(source.getPlaceOfBirthFacility()));
		target.setPlaceOfBirthFacilityDetails(source.getPlaceOfBirthFacilityDetails());
		target.setGestationAgeAtBirth(source.getGestationAgeAtBirth());
		target.setBirthWeight(source.getBirthWeight());

		target.setPassportNumber(source.getPassportNumber());
		target.setNationalHealthId(source.getNationalHealthId());
		target.setPlaceOfBirthFacilityType(source.getPlaceOfBirthFacilityType());
		target.setSymptomJournalStatus(source.getSymptomJournalStatus());

		target.setHasCovidApp(source.isHasCovidApp());
		target.setCovidCodeDelivered(source.isCovidCodeDelivered());
		target.setExternalId(source.getExternalId());
		target.setExternalToken(source.getExternalToken());

		target.setBirthCountry(CountryFacadeEjb.toReferenceDto(source.getBirthCountry()));
		target.setCitizenship(CountryFacadeEjb.toReferenceDto(source.getCitizenship()));

		return target;
	}

	@Override
	public long count(PersonCriteria criteria) {
		final CriteriaBuilder cb = em.getCriteriaBuilder();
		final CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		final Root<Person> person = cq.from(Person.class);

		final PersonQueryContext personQueryContext = new PersonQueryContext(cb, cq, person);

		Predicate filter = personService.createUserFilter(cb, cq, person);
		if (criteria != null) {
			final Predicate criteriaFilter = personService.buildCriteriaFilter(criteria, personQueryContext);
			filter = CriteriaBuilderHelper.and(cb, filter, criteriaFilter);
		}
		if (filter != null) {
			cq.where(filter);
		}
		cq.select(cb.countDistinct(person));
		return em.createQuery(cq).getSingleResult();
	}

	@Override
	public boolean exists(String uuid) {
		return personService.exists(uuid);
	}

	@Override
	public boolean doesExternalTokenExist(String externalToken, String personUuid) {
		return personService.exists(
			(cb, personRoot) -> CriteriaBuilderHelper
				.and(cb, cb.equal(personRoot.get(Person.EXTERNAL_TOKEN), externalToken), cb.notEqual(personRoot.get(Person.UUID), personUuid)));
	}

	@Override
	public long setMissingGeoCoordinates(boolean overwriteExistingCoordinates) {

		// The list is automatically filtered by the users jurisdiction
		List<PersonIndexDto> indexList = getIndexList(null, null, null, null);
		if (CollectionUtils.isEmpty(indexList)) {
			return 0;
		}

		// Run updates in batches to avoid large JPA cache
		List<String> personUuidList = indexList.stream().map(PersonIndexDto::getUuid).collect(Collectors.toList());
		List<Long> batchResults = new ArrayList<>();
		IterableHelper.executeBatched(personUuidList, 100, batchedUuids -> {
			batchResults.add(personService.updateGeoLocation(batchedUuids, overwriteExistingCoordinates));
		});

		Long changedPersons = batchResults.stream().reduce(0L, Long::sum);
		return changedPersons;
	}

	/**
	 * Makes sure that there is no invalid data associated with this person. For example, when the present condition
	 * is set to "Alive", all fields depending on the status being "Dead" or "Buried" are cleared.
	 */
	private void cleanUp(Person person) {

		if (person.getPresentCondition() == null
			|| person.getPresentCondition() == PresentCondition.ALIVE
			|| person.getPresentCondition() == PresentCondition.UNKNOWN) {
			person.setDeathDate(null);
			person.setCauseOfDeath(null);
			person.setCauseOfDeathDisease(null);
			person.setCauseOfDeathDetails(null);
			person.setDeathPlaceType(null);
			person.setDeathPlaceDescription(null);
		}
		if (!PresentCondition.BURIED.equals(person.getPresentCondition())) {
			person.setBurialDate(null);
			person.setBurialPlaceDescription(null);
			person.setBurialConductor(null);
		}
	}

	public void onPersonChanged(PersonDto existingPerson, Person newPerson) {

		List<Case> personCases = caseService.findBy(new CaseCriteria().person(new PersonReferenceDto(newPerson.getUuid())), true);
		// Call onCaseChanged once for every case to update case classification
		// Attention: this may lead to infinite recursion when not properly implemented
		for (Case personCase : personCases) {
			CaseDataDto existingCase = CaseFacadeEjbLocal.toDto(personCase);
			caseFacade.onCaseChanged(existingCase, personCase);
		}

		// Update cases if present condition has changed
		if (existingPerson != null) {
			if (newPerson.getPresentCondition() != null && existingPerson.getPresentCondition() != newPerson.getPresentCondition()) {
				// Update case list after previous onCaseChanged
				personCases = caseService.findBy(new CaseCriteria().person(new PersonReferenceDto(newPerson.getUuid())), true);
				for (Case personCase : personCases) {
					if (newPerson.getPresentCondition().isDeceased()) {
						if (personCase.getOutcome() == CaseOutcome.NO_OUTCOME) {
							CaseDataDto existingCase = CaseFacadeEjbLocal.toDto(personCase);
							personCase.setOutcome(CaseOutcome.DECEASED);
							personCase.setOutcomeDate(new Date());
							// Attention: this may lead to infinite recursion when not properly implemented
							caseFacade.onCaseChanged(existingCase, personCase);
						}
					} else if (personCase.getOutcome() == CaseOutcome.DECEASED) {
						CaseDataDto existingCase = CaseFacadeEjbLocal.toDto(personCase);
						personCase.setOutcome(CaseOutcome.NO_OUTCOME);
						personCase.setOutcomeDate(null);
						// Attention: this may lead to infinite recursion when not properly implemented
						caseFacade.onCaseChanged(existingCase, personCase);
					}
				}
			}
		}

		// Set approximate age if it hasn't been set before
		if (newPerson.getApproximateAge() == null && newPerson.getBirthdateYYYY() != null) {
			Pair<Integer, ApproximateAgeType> pair = ApproximateAgeHelper
				.getApproximateAge(newPerson.getBirthdateYYYY(), newPerson.getBirthdateMM(), newPerson.getBirthdateDD(), newPerson.getDeathDate());
			newPerson.setApproximateAge(pair.getElement0());
			newPerson.setApproximateAgeType(pair.getElement1());
			newPerson.setApproximateAgeReferenceDate(newPerson.getDeathDate() != null ? newPerson.getDeathDate() : new Date());
		}

		// Update caseAge of all associated cases when approximateAge has changed
		if ((existingPerson == null && newPerson.getApproximateAge() != null)
			|| (existingPerson != null && existingPerson.getApproximateAge() != newPerson.getApproximateAge())) {
			// Update case list after previous onCaseChanged
			personCases = caseService.findBy(new CaseCriteria().person(new PersonReferenceDto(newPerson.getUuid())), true);
			for (Case personCase : personCases) {
				CaseDataDto existingCase = CaseFacadeEjbLocal.toDto(personCase);
				if (newPerson.getApproximateAge() == null) {
					personCase.setCaseAge(null);
				} else if (newPerson.getApproximateAgeType() == ApproximateAgeType.MONTHS) {
					personCase.setCaseAge(0);
				} else {
					Date now = new Date();
					personCase.setCaseAge(newPerson.getApproximateAge() - DateHelper.getYearsBetween(personCase.getReportDate(), now));
					if (personCase.getCaseAge() < 0) {
						personCase.setCaseAge(0);
					}
				}
				caseFacade.onCaseChanged(existingCase, personCase);
			}
		}

		cleanUp(newPerson);
	}

	@Override
	public List<PersonIndexDto> getIndexList(PersonCriteria criteria, Integer first, Integer max, List<SortProperty> sortProperties) {

		final CriteriaBuilder cb = em.getCriteriaBuilder();
		final CriteriaQuery<PersonIndexDto> cq = cb.createQuery(PersonIndexDto.class);
		final Root<Person> person = cq.from(Person.class);

		final PersonQueryContext personQueryContext = new PersonQueryContext(cb, cq, person);

		final Join<Person, Location> location = person.join(Person.ADDRESS, JoinType.LEFT);
		final Join<Location, District> district = location.join(Location.DISTRICT, JoinType.LEFT);

		final Subquery<String> phoneSubQuery = cq.subquery(String.class);
		final Root<PersonContactDetail> phoneRoot = phoneSubQuery.from(PersonContactDetail.class);
		phoneSubQuery.where(
			cb.and(
				cb.equal(phoneRoot.get(PersonContactDetail.PERSON), person),
				cb.isTrue(phoneRoot.get(PersonContactDetail.PRIMARY_CONTACT)),
				cb.equal(phoneRoot.get(PersonContactDetail.PERSON_CONTACT_DETAIL_TYPE), PersonContactDetailType.PHONE)));
		phoneSubQuery.select(phoneRoot.get(PersonContactDetail.CONTACT_INFORMATION));

		final Subquery<String> emailSubQuery = cq.subquery(String.class);
		final Root<PersonContactDetail> emailRoot = emailSubQuery.from(PersonContactDetail.class);
		emailSubQuery.where(
			cb.and(
				cb.equal(emailRoot.get(PersonContactDetail.PERSON), person),
				cb.isTrue(emailRoot.get(PersonContactDetail.PRIMARY_CONTACT)),
				cb.equal(emailRoot.get(PersonContactDetail.PERSON_CONTACT_DETAIL_TYPE), PersonContactDetailType.EMAIL)));
		emailSubQuery.select(emailRoot.get(PersonContactDetail.CONTACT_INFORMATION));

		final Predicate jurisdictionPredicate = personService.getJurisdictionPredicate(cb, cq, person);

		// make sure to check the sorting by the multi-select order if you extend the selections here
		cq.multiselect(
			person.get(Person.UUID),
			person.get(Person.FIRST_NAME),
			person.get(Person.LAST_NAME),
			person.get(Person.APPROXIMATE_AGE),
			person.get(Person.APPROXIMATE_AGE_TYPE),
			person.get(Person.BIRTHDATE_DD),
			person.get(Person.BIRTHDATE_MM),
			person.get(Person.BIRTHDATE_YYYY),
			person.get(Person.SEX),
			district.get(District.NAME),
			location.get(Location.STREET),
			location.get(Location.HOUSE_NUMBER),
			location.get(Location.POSTAL_CODE),
			location.get(Location.CITY),
			phoneSubQuery.alias(PersonIndexDto.PHONE),
			emailSubQuery.alias(PersonIndexDto.EMAIL_ADDRESS),
			person.get(Person.CHANGE_DATE),
			cb.selectCase().when(jurisdictionPredicate, cb.literal(true)).otherwise(cb.literal(false)));

		Predicate filter = personService.createUserFilter(cb, cq, person);
		if (criteria != null) {
			final Predicate criteriaFilter = personService.buildCriteriaFilter(criteria, personQueryContext);
			filter = CriteriaBuilderHelper.and(cb, filter, criteriaFilter);
		}

		if (filter != null) {
			cq.where(filter);
		}
		cq.distinct(true);

		if (sortProperties != null && sortProperties.size() > 0) {
			List<Order> order = new ArrayList<Order>(sortProperties.size());
			for (SortProperty sortProperty : sortProperties) {
				Expression<?> expression;
				switch (sortProperty.propertyName) {
				case PersonIndexDto.UUID:
				case PersonIndexDto.FIRST_NAME:
				case PersonIndexDto.LAST_NAME:
				case PersonIndexDto.SEX:
					expression = person.get(sortProperty.propertyName);
					break;
				case PersonIndexDto.PHONE:
					expression = cb.literal(15); // order in the multiselect - Postgres limitation - needed to make sure it uses the same expression for ordering
					break;
				case PersonIndexDto.EMAIL_ADDRESS:
					expression = cb.literal(16); // order in the multiselect - Postgres limitation - needed to make sure it uses the same expression for ordering
					break;
				case PersonIndexDto.AGE_AND_BIRTH_DATE:
					expression = person.get(Person.APPROXIMATE_AGE);
					break;
				case PersonIndexDto.DISTRICT:
					expression = district.get(District.NAME);
					break;
				case PersonIndexDto.STREET:
				case PersonIndexDto.HOUSE_NUMBER:
				case PersonIndexDto.POSTAL_CODE:
				case PersonIndexDto.CITY:
					expression = location.get(sortProperty.propertyName);
					break;
				default:
					throw new IllegalArgumentException(sortProperty.propertyName);
				}
				order.add(sortProperty.ascending ? cb.asc(expression) : cb.desc(expression));
			}
			cq.orderBy(order);
		} else {
			cq.orderBy(cb.desc(person.get(Person.CHANGE_DATE)));
		}

		List<PersonIndexDto> persons;
		if (first != null && max != null) {
			persons = em.createQuery(cq).setFirstResult(first).setMaxResults(max).getResultList();
		} else {
			persons = em.createQuery(cq).getResultList();
		}

		Pseudonymizer pseudonymizer = Pseudonymizer.getDefault(userService::hasRight, I18nProperties.getCaption(Captions.inaccessibleValue));
		pseudonymizer.pseudonymizeDtoCollection(
			PersonIndexDto.class,
			persons,
			p -> p.getInJurisdiction(),
			(p, isInJurisdiction) -> pseudonymizer.pseudonymizeDto(AgeAndBirthDateDto.class, p.getAgeAndBirthDate(), isInJurisdiction, null));

		return persons;
	}

	private List<PersonDto> toPseudonymizedDtos(List<Person> persons) {
		final Pseudonymizer pseudonymizer = Pseudonymizer.getDefault(userService::hasRight);
		final List<Long> inJurisdictionIDs = personService.getInJurisdictionIDs(persons);

		return persons.stream().map(p -> convertToDto(p, pseudonymizer, inJurisdictionIDs.contains(p.getId()))).collect(Collectors.toList());
	}

	public PersonDto convertToDto(Person p, Pseudonymizer pseudonymizer, boolean inJurisdiction) {
		final PersonDto personDto = toDto(p);
		pseudonymizeDto(personDto, pseudonymizer, inJurisdiction);

		return personDto;
	}

	private void pseudonymizeDto(PersonDto dto, Pseudonymizer pseudonymizer, boolean isInJurisdiction) {
		if (dto != null) {
			pseudonymizer.pseudonymizeDto(PersonDto.class, dto, isInJurisdiction, p -> {
				pseudonymizer.pseudonymizeDto(LocationDto.class, p.getAddress(), isInJurisdiction, null);
				p.getAddresses().forEach(l -> pseudonymizer.pseudonymizeDto(LocationDto.class, l, isInJurisdiction, null));
				p.getPersonContactDetails().forEach(pcd -> pseudonymizer.pseudonymizeDto(PersonContactDetailDto.class, pcd, isInJurisdiction, null));
			});
		}
	}

	private void restorePseudonymizedDto(PersonDto source, Person person, PersonDto existingPerson) {
		if (person != null && existingPerson != null) {
			boolean isInJurisdiction = isPersonInJurisdiction(person);
			Pseudonymizer pseudonymizer = Pseudonymizer.getDefault(userService::hasRight);
			pseudonymizer.restorePseudonymizedValues(PersonDto.class, source, existingPerson, isInJurisdiction);
			pseudonymizer.restorePseudonymizedValues(LocationDto.class, source.getAddress(), existingPerson.getAddress(), isInJurisdiction);
			source.getAddresses()
				.forEach(
					l -> pseudonymizer.restorePseudonymizedValues(
						LocationDto.class,
						l,
						existingPerson.getAddresses().stream().filter(a -> a.getUuid().equals(l.getUuid())).findFirst().orElse(null),
						isInJurisdiction));
			source.getPersonContactDetails()
				.forEach(
					pcd -> pseudonymizer.restorePseudonymizedValues(
						PersonContactDetailDto.class,
						pcd,
						existingPerson.getPersonContactDetails().stream().filter(a -> a.getUuid().equals(pcd.getUuid())).findFirst().orElse(null),
						isInJurisdiction));
		}
	}

	private boolean isPersonInJurisdiction(Person person) {
		return !personService.getInJurisdictionIDs(Collections.singletonList(person)).isEmpty();
	}

	public static PersonReferenceDto toReferenceDto(Person entity) {

		if (entity == null) {
			return null;
		}
		return new PersonReferenceDto(entity.getUuid(), entity.getFirstName(), entity.getLastName());
	}

	public Person fillOrBuildEntity(@NotNull PersonDto source, Person target, boolean checkChangeDate) {

		target = DtoHelper.fillOrBuildEntity(source, target, personService::createPerson, checkChangeDate);

		target.setFirstName(source.getFirstName());
		target.setLastName(source.getLastName());
		target.setSalutation(source.getSalutation());
		target.setOtherSalutation(source.getOtherSalutation());
		target.setSex(source.getSex());

		target.setPresentCondition(source.getPresentCondition());
		target.setBirthdateDD(source.getBirthdateDD());
		target.setBirthdateMM(source.getBirthdateMM());
		target.setBirthdateYYYY(source.getBirthdateYYYY());
		target.setApproximateAge(source.getApproximateAge());
		target.setApproximateAgeType(source.getApproximateAgeType());
		target.setApproximateAgeReferenceDate(source.getApproximateAgeReferenceDate());
		target.setCauseOfDeath(source.getCauseOfDeath());
		target.setCauseOfDeathDetails(source.getCauseOfDeathDetails());
		target.setCauseOfDeathDisease(source.getCauseOfDeathDisease());
		target.setDeathDate(source.getDeathDate());
		target.setDeathPlaceType(source.getDeathPlaceType());
		target.setDeathPlaceDescription(source.getDeathPlaceDescription());
		target.setBurialDate(source.getBurialDate());
		target.setBurialPlaceDescription(source.getBurialPlaceDescription());
		target.setBurialConductor(source.getBurialConductor());

		target.setBirthName(source.getBirthName());
		target.setNickname(source.getNickname());
		target.setMothersMaidenName(source.getMothersMaidenName());

		target.setAddress(locationFacade.fromDto(source.getAddress(), checkChangeDate));
		List<Location> locations = new ArrayList<>();
		for (LocationDto locationDto : source.getAddresses()) {
			Location location = locationFacade.fromDto(locationDto, checkChangeDate);
			locations.add(location);
		}
		if (!DataHelper.equal(target.getAddresses(), locations)) {
			target.setChangeDateOfEmbeddedLists(new Date());
		}
		target.getAddresses().clear();
		target.getAddresses().addAll(locations);

		Person finalTarget = target;
		target.setPersonContactDetails(source.getPersonContactDetails().stream().map(dto -> {
			PersonContactDetail personContactDetail =
				DtoHelper.fillOrBuildEntity(dto, personContactDetailService.getByUuid(dto.getUuid()), PersonContactDetail::new, checkChangeDate);
			personContactDetail.setPerson(finalTarget);
			personContactDetail.setPrimaryContact(dto.isPrimaryContact());
			personContactDetail.setPersonContactDetailType(dto.getPersonContactDetailType());
			personContactDetail.setPhoneNumberType(dto.getPhoneNumberType());
			personContactDetail.setDetails(dto.getDetails());
			personContactDetail.setContactInformation(dto.getContactInformation());
			personContactDetail.setAdditionalInformation(dto.getAdditionalInformation());
			personContactDetail.setThirdParty(dto.isThirdParty());
			personContactDetail.setThirdPartyRole(dto.getThirdPartyRole());
			personContactDetail.setThirdPartyName(dto.getThirdPartyName());
			return personContactDetail;
		}).collect(Collectors.toSet()));

		target.setEducationType(source.getEducationType());
		target.setEducationDetails(source.getEducationDetails());

		target.setOccupationType(source.getOccupationType());
		target.setOccupationDetails(source.getOccupationDetails());
		target.setArmedForcesRelationType(source.getArmedForcesRelationType());

		target.setMothersName(source.getMothersName());
		target.setFathersName(source.getFathersName());
		target.setNamesOfGuardians(source.getNamesOfGuardians());
		target.setPlaceOfBirthRegion(regionService.getByReferenceDto(source.getPlaceOfBirthRegion()));
		target.setPlaceOfBirthDistrict(districtService.getByReferenceDto(source.getPlaceOfBirthDistrict()));
		target.setPlaceOfBirthCommunity(communityService.getByReferenceDto(source.getPlaceOfBirthCommunity()));
		target.setPlaceOfBirthFacility(facilityService.getByReferenceDto(source.getPlaceOfBirthFacility()));
		target.setPlaceOfBirthFacilityDetails(source.getPlaceOfBirthFacilityDetails());
		target.setGestationAgeAtBirth(source.getGestationAgeAtBirth());
		target.setBirthWeight(source.getBirthWeight());

		target.setPassportNumber(source.getPassportNumber());
		target.setNationalHealthId(source.getNationalHealthId());
		target.setPlaceOfBirthFacilityType(source.getPlaceOfBirthFacilityType());
		target.setSymptomJournalStatus(source.getSymptomJournalStatus());

		target.setHasCovidApp(source.isHasCovidApp());
		target.setCovidCodeDelivered(source.isCovidCodeDelivered());
		target.setExternalId(source.getExternalId());
		target.setExternalToken(source.getExternalToken());

		target.setBirthCountry(countryService.getByReferenceDto(source.getBirthCountry()));
		target.setCitizenship(countryService.getByReferenceDto(source.getCitizenship()));

		return target;
	}

	// needed for tests
	public void setExternalJournalService(ExternalJournalService externalJournalService) {
		this.externalJournalService = externalJournalService;
	}

	public boolean isPersonSimilarToExisting(PersonDto referencePerson) {

		PersonSimilarityCriteria criteria = new PersonSimilarityCriteria().firstName(referencePerson.getFirstName())
			.lastName(referencePerson.getLastName())
			.sex(referencePerson.getSex())
			.birthdateDD(referencePerson.getBirthdateDD())
			.birthdateMM(referencePerson.getBirthdateMM())
			.birthdateYYYY(referencePerson.getBirthdateYYYY())
			.passportNumber(referencePerson.getPassportNumber())
			.nationalHealthId(referencePerson.getNationalHealthId());

		return checkMatchingNameInDatabase(userFacade.getCurrentUser().toReference(), criteria);
	}

	@LocalBean
	@Stateless
	public static class PersonFacadeEjbLocal extends PersonFacadeEjb {

	}
}
