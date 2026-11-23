from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Path, Query, Response, status
from pydantic import MISSING, BaseModel, ConfigDict, Field

from subtrack.api.dependencies import catalog_service
from subtrack.api.parameters import INT64_MAX, QueryParameters, path_id
from subtrack.api.types import ApiTimestamp
from subtrack.persistence.types import StoredPlan
from subtrack.service.catalog import (
    UNSET,
    CatalogChanges,
    CatalogEntry,
    CatalogService,
    NewCatalogEntry,
)

type Category = Literal[
    "music",
    "video",
    "vpn",
    "cloud",
    "ai",
    "software",
    "domains",
    "gaming",
    "news",
    "education",
    "other",
]
type IntervalUnit = Literal["day", "week", "month", "year"]
type ServiceName = Annotated[str, Field(min_length=1, max_length=200)]
type PlanName = Annotated[str, Field(min_length=1, max_length=200)]
type Domain = Annotated[
    str,
    Field(
        max_length=253,
        pattern=r"^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$",
    ),
]
type Cents = Annotated[int, Field(ge=0, le=INT64_MAX)]
type IntervalCount = Annotated[int, Field(ge=1, le=366)]
type PlanList = Annotated[list[CatalogPlanInput], Field(max_length=50)]

_REQUEST = ConfigDict(extra="forbid", strict=True, frozen=True)
_RESPONSE = ConfigDict(extra="forbid", frozen=True)


class CatalogPlanInput(BaseModel):
    model_config = _REQUEST

    plan: PlanName
    price_cents: Cents | None = None
    interval_count: IntervalCount
    interval_unit: IntervalUnit

    def stored(self) -> StoredPlan:
        return StoredPlan(
            plan=self.plan,
            price_cents=self.price_cents,
            interval_count=self.interval_count,
            interval_unit=self.interval_unit,
        )


class CatalogServiceCreate(BaseModel):
    model_config = _REQUEST

    name: ServiceName
    domain: Domain | None = None
    category: Category
    plans: PlanList


class CatalogServiceUpdate(BaseModel):
    model_config = _REQUEST

    name: ServiceName | MISSING = MISSING
    domain: Domain | MISSING | None = MISSING
    category: Category | MISSING = MISSING
    plans: PlanList | MISSING = MISSING
    hidden: bool | MISSING = MISSING


class CatalogPlanResponse(BaseModel):
    model_config = _RESPONSE

    plan: str
    price_cents: int | None
    interval_count: int
    interval_unit: str


class CatalogServiceResponse(BaseModel):
    model_config = _RESPONSE

    id: int
    name: str
    domain: str | None
    category: str
    plans: list[CatalogPlanResponse]
    builtin: bool
    hidden: bool
    created_at: ApiTimestamp
    updated_at: ApiTimestamp


class CatalogServiceList(BaseModel):
    model_config = _RESPONSE

    items: list[CatalogServiceResponse]


router = APIRouter(prefix="/catalog", tags=["catalog"])

type Catalog = Annotated[CatalogService, Depends(catalog_service)]
type IdParameter = Annotated[str, Path(alias="id")]


@router.api_route("", methods=["GET", "HEAD"])
def list_catalog_services(
    catalog: Catalog,
    q: Annotated[str | None, Query()] = None,
    include_hidden: Annotated[str | None, Query()] = None,
) -> CatalogServiceList:
    parameters = QueryParameters()
    search = parameters.text("q", q, min_length=1, max_length=100)
    hidden = parameters.boolean("include_hidden", include_hidden, default=False)
    parameters.check()
    entries = catalog.search(q=search, include_hidden=hidden)
    return CatalogServiceList(items=[_response(entry) for entry in entries])


@router.post("", status_code=status.HTTP_201_CREATED)
def create_catalog_service(body: CatalogServiceCreate, catalog: Catalog) -> CatalogServiceResponse:
    entry = catalog.create(
        NewCatalogEntry(
            name=body.name,
            domain=body.domain,
            category=body.category,
            plans=tuple(plan.stored() for plan in body.plans),
        )
    )
    return _response(entry)


@router.patch("/{id}")
def update_catalog_service(
    service_id: IdParameter, body: CatalogServiceUpdate, catalog: Catalog
) -> CatalogServiceResponse:
    changes = CatalogChanges(
        name=UNSET if body.name is MISSING else body.name,
        domain=UNSET if body.domain is MISSING else body.domain,
        category=UNSET if body.category is MISSING else body.category,
        plans=UNSET if body.plans is MISSING else tuple(plan.stored() for plan in body.plans),
        hidden=UNSET if body.hidden is MISSING else body.hidden,
    )
    return _response(catalog.update(path_id(service_id), changes))


@router.delete("/{id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_catalog_service(service_id: IdParameter, catalog: Catalog) -> Response:
    catalog.delete(path_id(service_id))
    return Response(status_code=status.HTTP_204_NO_CONTENT)


def _response(entry: CatalogEntry) -> CatalogServiceResponse:
    return CatalogServiceResponse(
        id=entry.id,
        name=entry.name,
        domain=entry.domain,
        category=entry.category,
        plans=[
            CatalogPlanResponse(
                plan=plan.plan,
                price_cents=plan.price_cents,
                interval_count=plan.interval_count,
                interval_unit=plan.interval_unit,
            )
            for plan in entry.plans
        ],
        builtin=entry.builtin,
        hidden=entry.hidden,
        created_at=entry.created_at,
        updated_at=entry.updated_at,
    )
