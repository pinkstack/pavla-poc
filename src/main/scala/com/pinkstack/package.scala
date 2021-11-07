package com

package object pinkstack {
  type Latitude = Double
  type Longitude = Double

  final case class Position(latitude: Latitude, longitude: Longitude)

  final case class Address(address: String, position: Position)

  final case class BikeStation(name: String,
                               address: Address,
                               state: String,
                               connectionState: String,
                               count: Int,
                               free: Int,
                               busy: Int)
}
