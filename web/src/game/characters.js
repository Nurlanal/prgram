// Playable characters. Add a new one by appending an entry - Player.js builds
// the 3D model from these colours and accessories automatically.

export const CHARACTERS = [
  {
    id: 'rashad', name: 'Rəşad', price: 0, desc: 'Bayraq jiletli bulvar qaçışçısı',
    skin: '#f2c6a0', hair: '#3a2416', hat: 'cap', hatColor: '#e0262f', top: '#f4f4f4',
    vest: '#2a63c9', vestPattern: 'flag', pants: '#4f86c6', shoes: '#f4f4f4', sole: '#c9a27a',
  },
  {
    id: 'elvin', name: 'Elvin', price: 800, desc: 'Qulaqcıqlı skeyter',
    skin: '#e8b48e', hair: '#1c120c', hat: 'headphones', hatColor: '#00b5e2', top: '#509e2f',
    vest: '#3a7a2a', vestPattern: 'stars', pants: '#2b2b33', shoes: '#ef3340', sole: '#ffffff',
  },
  {
    id: 'leyla', name: 'Leyla', price: 1500, desc: 'Buta naxışlı gödəkcə',
    skin: '#f5cfae', hair: '#5a2e16', hat: 'ponytail', hatColor: '#ef3340', top: '#ffd9e0',
    vest: '#c2185b', vestPattern: 'buta', pants: '#30304a', shoes: '#ffffff', sole: '#ef3340',
  },
  {
    id: 'aysel', name: 'Aysel', price: 2500, desc: 'Kəlağayı və xalça naxışı',
    skin: '#f0c49c', hair: '#2b1a10', hat: 'kelaghayi', hatColor: '#b3202a', top: '#f2c14e',
    vest: '#8f1d2c', vestPattern: 'carpet', pants: '#1d3f8f', shoes: '#f2c14e', sole: '#8f1d2c',
  },
  {
    id: 'babek', name: 'Babək', price: 3500, desc: 'Papaq və çuxa — milli geyim',
    skin: '#e2a982', hair: '#1a1a1a', hat: 'papaq', hatColor: '#2a2420', top: '#f4ead6',
    vest: '#5b1a1a', vestPattern: 'plain', pants: '#1c1c22', shoes: '#3a2416', sole: '#1a1a1a', beard: true,
  },
  {
    id: 'futbol', name: 'Kamran', price: 5000, desc: 'Futbol forması, 10 nömrə',
    skin: '#d9a07a', hair: '#111111', hat: 'none', hatColor: '#000', top: '#ffffff',
    vest: '#ffffff', vestPattern: 'stripes', pants: '#111111', shoes: '#ffd34d', sole: '#111111',
  },
];

export function getCharacter(id) {
  return CHARACTERS.find((c) => c.id === id) || CHARACTERS[0];
}
