# Event Equipment Request API

Base URL: `/api/v1/event-equipment-requests`

All endpoints require a valid JWT access token:
```
Authorization: Bearer <access_token>
```

---

## Status Lifecycle

```
PENDING_REVIEW → APPROVED → ACTIVE → RETURNED
PENDING_REVIEW → REJECTED
PENDING_REVIEW → CANCELLED  (by requester)
APPROVED       → CANCELLED  (by requester)
```

---

## Endpoints

### 1. Submit Equipment Request
**Role required:** `EVENT_COMMITTEE`

The requesting user must be an assigned committee member of the target event.

```
POST /api/v1/event-equipment-requests
```

**Request Body:**
```json
{
  "eventId": 1,
  "equipmentIds": [3, 7],
  "startDate": "2026-06-01",
  "endDate": "2026-06-03",
  "notes": "Needed for photo coverage during the opening ceremony"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `eventId` | Long | Yes | ID of the event this request is for |
| `equipmentIds` | Long[] | Yes | IDs of `MainEquipment` items to request |
| `startDate` | String (YYYY-MM-DD) | Yes | Requested start date |
| `endDate` | String (YYYY-MM-DD) | Yes | Requested end date (must be ≥ startDate) |
| `notes` | String | No | Optional notes from the requester |

**Response `201 Created`:**
```json
{
  "id": 12,
  "requestNumber": "EER-2026-000012",
  "eventId": 1,
  "eventName": "Annual Photography Exhibition",
  "requestedByUsername": "ali_event",
  "reviewedByUsername": null,
  "status": "PENDING_REVIEW",
  "requestedStartDate": "2026-06-01",
  "requestedEndDate": "2026-06-03",
  "approvedStartDate": null,
  "approvedEndDate": null,
  "durationDays": null,
  "rejectionReason": null,
  "committeeNotes": null,
  "requesterNotes": "Needed for photo coverage during the opening ceremony",
  "items": [
    {
      "id": 21,
      "mainEquipmentId": 3,
      "equipmentType": "Camera",
      "brand": "Canon",
      "model": "EOS R5",
      "serialNumber": "SN-CAM-001"
    },
    {
      "id": 22,
      "mainEquipmentId": 7,
      "equipmentType": "Lens",
      "brand": "Canon",
      "model": "RF 24-70mm",
      "serialNumber": "SN-LNS-002"
    }
  ],
  "createdAt": "2026-05-19T10:30:00"
}
```

**Error responses:**
| Status | Reason |
|---|---|
| `403 Forbidden` | Requesting user is not a committee member of the event |
| `404 Not Found` | Event or equipment ID does not exist |
| `400 Bad Request` | endDate before startDate, or equipmentIds is empty |
| `409 Conflict` | One or more equipment items already have an approved/active request for those dates |

---

### 2. Get Requests for a Specific Event
**Role required:** `EVENT_COMMITTEE` (own events only) or `EQUIPMENT_COMMITTEE`

```
GET /api/v1/event-equipment-requests/event/{eventId}
```

**Path parameter:** `eventId` — ID of the event

**Response `200 OK`:** Array of request objects (same shape as the submit response)
```json
[
  {
    "id": 12,
    "requestNumber": "EER-2026-000012",
    "eventId": 1,
    "eventName": "Annual Photography Exhibition",
    "requestedByUsername": "ali_event",
    "reviewedByUsername": "sarah_equip",
    "status": "APPROVED",
    "requestedStartDate": "2026-06-01",
    "requestedEndDate": "2026-06-03",
    "approvedStartDate": "2026-06-01",
    "approvedEndDate": "2026-06-03",
    "durationDays": 3,
    "rejectionReason": null,
    "committeeNotes": "Approved — equipment reserved.",
    "requesterNotes": "Needed for photo coverage during the opening ceremony",
    "items": [
      {
        "id": 21,
        "mainEquipmentId": 3,
        "equipmentType": "Camera",
        "brand": "Canon",
        "model": "EOS R5",
        "serialNumber": "SN-CAM-001"
      }
    ],
    "createdAt": "2026-05-19T10:30:00"
  }
]
```

**Error responses:**
| Status | Reason |
|---|---|
| `403 Forbidden` | EVENT_COMMITTEE user is not in this event's committee |
| `404 Not Found` | Event ID does not exist |

---

### 3. Cancel a Request
**Role required:** `EVENT_COMMITTEE` (own requests only)

Only `PENDING_REVIEW` or `APPROVED` requests can be cancelled.

```
DELETE /api/v1/event-equipment-requests/{id}
```

**Path parameter:** `id` — ID of the equipment request

**Response `200 OK`:** Updated request object with `"status": "CANCELLED"`

**Error responses:**
| Status | Reason |
|---|---|
| `403 Forbidden` | Request does not belong to the calling user |
| `400 Bad Request` | Request is not in `PENDING_REVIEW` or `APPROVED` status |
| `404 Not Found` | Request ID does not exist |

---

### 4. Get All Requests (Paginated)
**Role required:** `EQUIPMENT_COMMITTEE`

```
GET /api/v1/event-equipment-requests?search=&status=&page=0&size=10
```

**Query parameters:**
| Param | Type | Default | Description |
|---|---|---|---|
| `search` | String | `""` | Filter by request number, requester username, full name, or event name |
| `status` | String | `""` | Filter by exact status (e.g. `PENDING_REVIEW`, `APPROVED`) |
| `page` | int | `0` | Zero-based page number |
| `size` | int | `10` | Items per page (max 100) |

**Response `200 OK`:** Spring Page object
```json
{
  "content": [
    {
      "id": 12,
      "requestNumber": "EER-2026-000012",
      "eventId": 1,
      "eventName": "Annual Photography Exhibition",
      "requestedByUsername": "ali_event",
      "reviewedByUsername": null,
      "status": "PENDING_REVIEW",
      "requestedStartDate": "2026-06-01",
      "requestedEndDate": "2026-06-03",
      "approvedStartDate": null,
      "approvedEndDate": null,
      "durationDays": null,
      "rejectionReason": null,
      "committeeNotes": null,
      "requesterNotes": "Needed for photo coverage",
      "items": [ "..." ],
      "createdAt": "2026-05-19T10:30:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

---

### 5. Review a Request (Approve or Reject)
**Role required:** `EQUIPMENT_COMMITTEE`

Only `PENDING_REVIEW` requests can be reviewed.

```
PATCH /api/v1/event-equipment-requests/{id}/review
```

**Path parameter:** `id` — ID of the equipment request

**Request Body — Approve:**
```json
{
  "action": "APPROVE",
  "approvedStartDate": "2026-06-01",
  "approvedEndDate": "2026-06-03",
  "equipmentIds": [3, 7],
  "committeeNotes": "Approved — equipment reserved."
}
```

**Request Body — Reject:**
```json
{
  "action": "REJECT",
  "rejectionReason": "Equipment is under maintenance during those dates.",
  "committeeNotes": "Please resubmit for a later date."
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `action` | String | Yes | `"APPROVE"` or `"REJECT"` |
| `approvedStartDate` | String (YYYY-MM-DD) | Required if APPROVE | Final approved start date |
| `approvedEndDate` | String (YYYY-MM-DD) | Required if APPROVE | Final approved end date |
| `equipmentIds` | Long[] | No | Override which equipment items to approve; defaults to requested items |
| `rejectionReason` | String | No | Reason shown to the requester on rejection |
| `committeeNotes` | String | No | Internal notes for committee records |

**Response `200 OK`:** Updated request object

On **approval**, response includes:
```json
{
  "status": "APPROVED",
  "approvedStartDate": "2026-06-01",
  "approvedEndDate": "2026-06-03",
  "durationDays": 3,
  "reviewedByUsername": "sarah_equip",
  "committeeNotes": "Approved — equipment reserved."
}
```

On **rejection**, response includes:
```json
{
  "status": "REJECTED",
  "rejectionReason": "Equipment is under maintenance during those dates.",
  "reviewedByUsername": "sarah_equip"
}
```

**Error responses:**
| Status | Reason |
|---|---|
| `400 Bad Request` | Request is not `PENDING_REVIEW`, action is invalid, or dates missing for approval |
| `409 Conflict` | Equipment already has an approved/active request in the approved date range |
| `404 Not Found` | Request or equipment ID does not exist |

---

### 6. Mark Request as Returned
**Role required:** `EQUIPMENT_COMMITTEE`

Only `ACTIVE` requests can be marked returned.

```
PATCH /api/v1/event-equipment-requests/{id}/mark-returned
```

**Request Body:** None

**Response `200 OK`:** Updated request object with `"status": "RETURNED"`

**Error responses:**
| Status | Reason |
|---|---|
| `400 Bad Request` | Request is not in `ACTIVE` status |
| `404 Not Found` | Request ID does not exist |

---

### 7. Trigger Active Status Check (Manual)
**Role required:** `EQUIPMENT_COMMITTEE`

Manually runs the scheduler that promotes `APPROVED` requests to `ACTIVE` when their `approvedStartDate` has been reached. Normally this runs automatically at midnight daily.

```
POST /api/v1/event-equipment-requests/trigger-active
```

**Request Body:** None

**Response `200 OK`:** Empty body

---

## Common Response Object Reference

### EquipmentRequestResponse

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | Long | No | Internal request ID |
| `requestNumber` | String | No | Human-readable ID, format `EER-YYYY-NNNNNN` |
| `eventId` | Long | No | Associated event ID |
| `eventName` | String | No | Associated event name |
| `requestedByUsername` | String | No | Username of the EVENT_COMMITTEE member who submitted |
| `reviewedByUsername` | String | Yes | Username of the EQUIPMENT_COMMITTEE member who reviewed; `null` if not yet reviewed |
| `status` | String | No | One of: `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `CANCELLED`, `ACTIVE`, `RETURNED` |
| `requestedStartDate` | String (YYYY-MM-DD) | No | Original requested start date |
| `requestedEndDate` | String (YYYY-MM-DD) | No | Original requested end date |
| `approvedStartDate` | String (YYYY-MM-DD) | Yes | Final approved start date; `null` until approved |
| `approvedEndDate` | String (YYYY-MM-DD) | Yes | Final approved end date; `null` until approved |
| `durationDays` | Integer | Yes | Duration in days (inclusive); `null` until approved |
| `rejectionReason` | String | Yes | Reason for rejection; `null` if not rejected |
| `committeeNotes` | String | Yes | Internal committee notes |
| `requesterNotes` | String | Yes | Notes from the requester at submission |
| `items` | Array | No | List of `MainEquipment` items in this request |
| `createdAt` | String (ISO datetime) | No | Timestamp when the request was submitted |

### EquipmentRequestItemResponse

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | Long | No | Item line ID |
| `mainEquipmentId` | Long | No | Equipment catalogue ID |
| `equipmentType` | String | No | Category (e.g. `"Camera"`, `"Lens"`, `"Tripod"`) |
| `brand` | String | Yes | Equipment brand |
| `model` | String | Yes | Equipment model |
| `serialNumber` | String | Yes | Equipment serial number |
